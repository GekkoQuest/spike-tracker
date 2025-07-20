class SpikeTracker {
    constructor() {
        this.stompClient = null;
        this.isConnected = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.reconnectDelay = 3000;
        this.currentTab = 'live';
        this.liveMatches = new Map();
        this.matchHistory = [];
        this.lastRenderHash = '';
        this.isInitialLoad = true;

        this.defaultTeamImage = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KICA8Y2lyY2xlIGN4PSIzMCIgY3k9IjMwIiByPSIyOCIgZmlsbD0iIzJkMzc0OCIgc3Ryb2tlPSIjZmY2YjM1IiBzdHJva2Utd2lkdGg9IjIiLz4KICA8dGV4dCB4PSIzMCIgeT0iMzciIHRleHQtYW5jaG9yPSJtaWRkbGUiIGZvbnQtZmFtaWx5PSJBcmlhbCwgc2Fucy1zZXJpZiIgZm9udC1zaXplPSIxNiIgZm9udC13ZWlnaHQ9ImJvbGQiIGZpbGw9IiNmZmZmZmYiPj88L3RleHQ+Cjwvc3ZnPg==';

        this.initializeWebSocket();
        this.initializeUI();
        this.startHealthCheck();
    }

    initializeWebSocket() {
        try {
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);

            this.stompClient.debug = () => {};

            this.stompClient.connect({},
                (frame) => this.onConnected(frame),
                (error) => this.onError(error)
            );
        } catch (error) {
            console.error('WebSocket initialization failed:', error);
            this.fallbackToPolling();
        }
    }

    onConnected(frame) {
        console.log('Connected to WebSocket');
        this.isConnected = true;
        this.reconnectAttempts = 0;
        this.updateConnectionStatus('connected');

        this.stompClient.subscribe('/topic/matches', (message) => {
            const matches = JSON.parse(message.body);
            this.updateLiveMatches(matches);
        });

        this.stompClient.send('/app/matches/subscribe', {}, JSON.stringify({}));
    }

    onError(error) {
        console.error('WebSocket connection error:', error);
        this.isConnected = false;
        this.updateConnectionStatus('disconnected');

        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            this.updateConnectionStatus('connecting');

            setTimeout(() => {
                console.log(`Reconnection attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts}`);
                this.initializeWebSocket();
            }, this.reconnectDelay * this.reconnectAttempts);
        } else {
            console.log('Max reconnection attempts reached, falling back to polling');
            this.fallbackToPolling();
        }
    }

    fallbackToPolling() {
        console.log('WebSocket failed, using polling fallback');
        this.updateConnectionStatus('connecting');

        setInterval(() => {
            this.fetchMatches();
        }, 10000);

        this.fetchMatches();
    }

    async fetchMatches() {
        if (this.isConnected) {
            return; // Don't poll if WebSocket is working
        }

        try {
            const response = await fetch('/api/matches');
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            const matches = await response.json();
            this.updateLiveMatches(matches);
            this.updateConnectionStatus('connected');
        } catch (error) {
            console.error('Failed to fetch matches:', error);
            this.updateConnectionStatus('disconnected');
        }
    }

    async fetchHistory() {
        try {
            const response = await fetch('/api/matches/history');
            if (!response.ok) throw new Error(`HTTP ${response.status}`);

            this.matchHistory = await response.json();
            this.renderHistory();
        } catch (error) {
            console.error('Failed to fetch match history:', error);
        }
    }

    updateLiveMatches(matches) {
        const matchesArray = Array.isArray(matches) ? matches : Object.values(matches);

        const newHash = this.createMatchHash(matchesArray);

        if (newHash !== this.lastRenderHash || this.isInitialLoad) {
            this.lastRenderHash = newHash;
            this.isInitialLoad = false;

            this.liveMatches.clear();
            matchesArray.forEach(match => {
                this.liveMatches.set(match.match_page, match);
            });

            this.renderLiveMatches();
            console.log('Updated UI with', matchesArray.length, 'matches');
        }

        this.updateMatchCount(matchesArray.length);
        this.updateLastUpdateTime();
    }

    createMatchHash(matches) {
        return JSON.stringify(matches.map(match => ({
            id: match.match_page,
            team1: match.team1,
            team2: match.team2,
            score1: match.score1,
            score2: match.score2,
            map: match.current_map,
            mapNumber: match.map_number,
            event: match.match_event,
            series: match.match_series,
            streamLink: match.streamLink,
            // Include round data if available
            t1_ct: match.team1_round_ct,
            t1_t: match.team1_round_t,
            t2_ct: match.team2_round_ct,
            t2_t: match.team2_round_t
        })));
    }

    renderLiveMatches() {
        const container = document.getElementById('liveMatchesContainer');
        const loading = document.getElementById('loading');
        const noMatches = document.getElementById('noLiveMatches');

        loading.style.display = 'none';

        if (this.liveMatches.size === 0) {
            container.style.display = 'none';
            noMatches.style.display = 'block';
            return;
        }

        container.style.display = 'grid';
        noMatches.style.display = 'none';

        container.innerHTML = Array.from(this.liveMatches.values())
            .map(match => this.createMatchCard(match))
            .join('');
    }

    createMatchCard(match) {
        const team1Logo = this.getValidImageUrl(match.team1_logo);
        const team2Logo = this.getValidImageUrl(match.team2_logo);

        const streamButton = match.streamLink ?
            `<a href="${match.streamLink}" target="_blank" class="btn btn-primary">🎥 Watch Live</a>` :
            `<button class="btn btn-secondary" disabled>Stream N/A</button>`;

        return `
            <div class="match-card">
                <div class="match-header">
                    <div class="live-indicator">
                        <span>🔴</span> LIVE
                    </div>
                    <div class="match-event">${this.escapeHtml(match.match_event || 'Unknown Event')}</div>
                    <div class="match-series">${this.escapeHtml(match.match_series || 'Unknown Series')}</div>
                </div>
                
                <div class="match-teams">
                    <div class="team">
                        <img src="${team1Logo}" alt="${this.escapeHtml(match.team1)}" class="team-logo" 
                             onerror="this.src='${this.defaultTeamImage}'; this.onerror=null;">
                        <div class="team-name">${this.escapeHtml(match.team1)}</div>
                        <div class="team-country">${this.getCountryName(match.flag1)}</div>
                    </div>
                    
                    <div class="vs-section">
                        <div class="vs-text">VS</div>
                        <div class="score">${match.score1 || '0'} - ${match.score2 || '0'}</div>
                    </div>
                    
                    <div class="team">
                        <img src="${team2Logo}" alt="${this.escapeHtml(match.team2)}" class="team-logo"
                             onerror="this.src='${this.defaultTeamImage}'; this.onerror=null;">
                        <div class="team-name">${this.escapeHtml(match.team2)}</div>
                        <div class="team-country">${this.getCountryName(match.flag2)}</div>
                    </div>
                </div>
                
                ${match.current_map ? `
                <div class="map-info">
                    <div class="current-map">📍 ${this.escapeHtml(match.current_map)}</div>
                    ${this.renderRoundDetails(match)}
                </div>
                ` : ''}
                
                <div class="match-actions">
                    ${streamButton}
                    <a href="${match.match_page}" target="_blank" class="btn btn-secondary">📊 Match Page</a>
                </div>
            </div>
        `;
    }

    getValidImageUrl(url) {
        if (!url || url.trim() === '' || url === 'null' || url === 'undefined') {
            return this.defaultTeamImage;
        }

        if (url.startsWith('https:/img/vlr/')) {
            url = url.replace('https:/img/vlr/', 'https://www.vlr.gg/img/vlr/');
        }

        try {
            if (url.startsWith('//')) {
                url = 'https:' + url;
            }

            const urlObj = new URL(url);

            if (urlObj.hostname === '' || urlObj.pathname === '' || url.includes('vlr.png')) {
                return this.defaultTeamImage;
            }

            return urlObj.href;
        } catch (e) {
            return this.defaultTeamImage;
        }
    }

    renderRoundDetails(match) {
        const hasRoundData = match.team1_round_ct && match.team1_round_ct !== 'N/A' && match.team1_round_ct !== '0' ||
            match.team1_round_t && match.team1_round_t !== 'N/A' && match.team1_round_t !== '0' ||
            match.team2_round_ct && match.team2_round_ct !== 'N/A' && match.team2_round_ct !== '0' ||
            match.team2_round_t && match.team2_round_t !== 'N/A' && match.team2_round_t !== '0';

        if (!hasRoundData) {
            const statusInfo = this.getMatchStatus(match);

            return `
                <div class="round-details">
                    <div class="match-status">
                        ${statusInfo}
                    </div>
                    <div class="round-info-unavailable">
                        Round details not available 
                        <span class="info-icon" onclick="showTooltip(event)">ⓘ</span>
                        <div class="tooltip-text" id="tooltip">
                            Round-by-round scores are generally only available for major tournaments and select matches. This match is live but detailed round information is not provided by the data source.
                        </div>
                    </div>
                </div>
            `;
        }

        return `
            <div class="round-details">
                <div>
                    <strong>${this.escapeHtml(match.team1)}</strong><br>
                    CT: ${match.team1_round_ct || '0'} | T: ${match.team1_round_t || '0'}
                </div>
                <div>
                    <strong>${this.escapeHtml(match.team2)}</strong><br>
                    CT: ${match.team2_round_ct || '0'} | T: ${match.team2_round_t || '0'}
                </div>
            </div>
        `;
    }

    getMatchStatus(match) {
        if (match.time_until_match && match.time_until_match !== '0' && match.time_until_match !== 'LIVE') {
            return `⏰ Starting in: ${match.time_until_match}`;
        }

        if (match.time_until_match === 'LIVE') {
            return `🔴 Match is LIVE`;
        }

        if (match.current_map && match.current_map.toLowerCase() !== 'tbd') {
            return `🗺️ Map: ${this.escapeHtml(match.current_map)}`;
        }

        if (match.score1 === '0' && match.score2 === '0') {
            return `🎮 Match starting soon...`;
        }

        if (match.map_number) {
            return `📊 Map ${match.map_number} in progress`;
        }

        return `🔴 Live match in progress`;
    }

    createInfoTooltip(message) {
        return `
            <div class="info-tooltip-container">
                <span class="info-icon" title="${this.escapeHtml(message)}">ⓘ</span>
                <div class="tooltip-text">${this.escapeHtml(message)}</div>
            </div>
        `;
    }

    renderHistory() {
        const container = document.getElementById('historyContainer');
        const noHistory = document.getElementById('noHistory');

        if (this.matchHistory.length === 0) {
            container.style.display = 'none';
            noHistory.style.display = 'block';
            return;
        }

        container.style.display = 'grid';
        noHistory.style.display = 'none';

        container.innerHTML = this.matchHistory
            .map(match => this.createHistoryCard(match))
            .join('');
    }

    createHistoryCard(match) {
        const winner = match.winner || 'Unknown';
        const completedTime = new Date(match.completedAt).toLocaleString();

        return `
            <div class="history-card">
                <div class="history-header">
                    <div class="history-teams">
                        ${this.getCountryName(match.flag1)} ${this.escapeHtml(match.team1)} vs 
                        ${this.escapeHtml(match.team2)} ${this.getCountryName(match.flag2)}
                    </div>
                    <div class="history-time">${completedTime}</div>
                </div>
                
                <div class="history-score">
                    ${match.finalScore1} - ${match.finalScore2}
                    ${winner !== 'Unknown' && winner !== 'Draw' ? `<span style="color: var(--success-color); font-size: 0.8em;">🏆 ${this.escapeHtml(winner)}</span>` : ''}
                </div>
                
                <div class="history-details">
                    <div><strong>Event:</strong> ${this.escapeHtml(match.match_event)}</div>
                    <div><strong>Map:</strong> ${this.escapeHtml(match.current_map || 'Unknown')}</div>
                    ${match.durationMinutes > 0 ? `<div><strong>Duration:</strong> ${match.durationMinutes} minutes</div>` : ''}
                </div>
            </div>
        `;
    }

    getCountryName(countryCode) {
        if (!countryCode) return '';

        let code = countryCode.toLowerCase().trim();

        if (code.startsWith('flag_')) {
            code = code.substring(5);
        }

        const countryMap = {
            'de': 'Germany',
            'eg': 'Egypt',
            'es': 'Spain',
            'fr': 'France',
            'us': 'United States',
            'gb': 'United Kingdom',
            'br': 'Brazil',
            'kr': 'South Korea',
            'jp': 'Japan',
            'cn': 'China',
            'ru': 'Russia',
            'ca': 'Canada',
            'au': 'Australia',
            'se': 'Sweden',
            'no': 'Norway',
            'dk': 'Denmark',
            'fi': 'Finland',
            'pl': 'Poland',
            'it': 'Italy',
            'pt': 'Portugal',
            'nl': 'Netherlands',
            'be': 'Belgium',
            'ch': 'Switzerland',
            'at': 'Austria',
            'cz': 'Czech Republic',
            'sk': 'Slovakia',
            'hu': 'Hungary',
            'ro': 'Romania',
            'bg': 'Bulgaria',
            'hr': 'Croatia',
            'rs': 'Serbia',
            'tr': 'Turkey',
            'ua': 'Ukraine',
            'gr': 'Greece',
            'ie': 'Ireland',
            'mx': 'Mexico',
            'ar': 'Argentina',
            'cl': 'Chile',
            'co': 'Colombia',
            'pe': 'Peru',
            've': 'Venezuela',
            'tw': 'Taiwan',
            'hk': 'Hong Kong',
            'sg': 'Singapore',
            'th': 'Thailand',
            'ph': 'Philippines',
            'id': 'Indonesia',
            'my': 'Malaysia',
            'vn': 'Vietnam',
            'in': 'India',
            'nz': 'New Zealand',
            'za': 'South Africa',
            'il': 'Israel',
            'ae': 'UAE',
            'sa': 'Saudi Arabia',
            'pk': 'Pakistan',
            'bd': 'Bangladesh',
            'lk': 'Sri Lanka',
            'np': 'Nepal',
            'mn': 'Mongolia',
            'kz': 'Kazakhstan',
            'uz': 'Uzbekistan',
            'kg': 'Kyrgyzstan',
            'tj': 'Tajikistan',
            'tm': 'Turkmenistan',
            'ge': 'Georgia',
            'am': 'Armenia',
            'az': 'Azerbaijan',
            'by': 'Belarus',
            'lt': 'Lithuania',
            'lv': 'Latvia',
            'ee': 'Estonia',
            'ba': 'Bosnia',
            'me': 'Montenegro',
            'mk': 'Macedonia',
            'al': 'Albania',
            'cy': 'Cyprus',
            'mt': 'Malta',
            'is': 'Iceland',
            'lu': 'Luxembourg',
            'li': 'Liechtenstein',
            'mc': 'Monaco',
            'ad': 'Andorra',
            'sm': 'San Marino',
            'va': 'Vatican',
            'ma': 'Morocco',
            'tn': 'Tunisia',
            'dz': 'Algeria',
            'ly': 'Libya',
            'sd': 'Sudan',
            'et': 'Ethiopia',
            'ke': 'Kenya',
            'ug': 'Uganda',
            'tz': 'Tanzania',
            'rw': 'Rwanda',
            'gh': 'Ghana',
            'ng': 'Nigeria',
            'cm': 'Cameroon',
            'ci': 'Ivory Coast',
            'sn': 'Senegal',
            'ml': 'Mali',
            'bf': 'Burkina Faso',
            'ne': 'Niger',
            'td': 'Chad',
            'ps': 'Palestine',
            'lb': 'Lebanon',
            'sy': 'Syria',
            'jo': 'Jordan',
            'iq': 'Iraq',
            'ir': 'Iran',
            'kw': 'Kuwait',
            'qa': 'Qatar',
            'bh': 'Bahrain',
            'om': 'Oman',
            'ye': 'Yemen',
            'af': 'Afghanistan',
            'eu': 'Europe',
            'na': 'North America',
            'asia': 'Asia',
            'oceania': 'Oceania',
            'africa': 'Africa',
            'world': 'International'
        };

        return countryMap[code] || code.toUpperCase();
    }

    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    updateConnectionStatus(status) {
        const statusElement = document.getElementById('connectionStatus');
        const apiStatus = document.getElementById('apiStatus');

        if (statusElement) {
            statusElement.className = `connection-status ${status}`;

            switch (status) {
                case 'connected':
                    statusElement.textContent = 'Connected';
                    break;
                case 'connecting':
                    statusElement.textContent = 'Connecting...';
                    break;
                case 'disconnected':
                    statusElement.textContent = 'Disconnected';
                    break;
            }
        }

        if (apiStatus) {
            apiStatus.className = `status-indicator ${status === 'connected' ? '' : 'offline'}`;
        }
    }

    updateMatchCount(count) {
        const element = document.getElementById('liveMatchCount');
        if (element) {
            element.textContent = `${count} Live Match${count !== 1 ? 'es' : ''}`;
        }
    }

    updateLastUpdateTime() {
        const element = document.getElementById('lastUpdate');
        if (element) {
            const now = new Date();
            element.textContent = `Last update: ${now.toLocaleTimeString()}`;
        }
    }

    startHealthCheck() {
        setInterval(async () => {
            try {
                const response = await fetch('/api/health');
                const health = await response.json();

                if (health.status === 'UP') {
                    if (!this.isConnected) {
                        this.updateConnectionStatus('connected');
                    }
                } else {
                    this.updateConnectionStatus('disconnected');
                }
            } catch (error) {
                this.updateConnectionStatus('disconnected');
            }
        }, 10000);
    }

    initializeUI() {
        window.showTab = (tabName) => {
            document.querySelectorAll('.nav-tab').forEach(tab => {
                tab.classList.remove('active');
            });
            event.target.classList.add('active');

            document.querySelectorAll('.tab-content').forEach(content => {
                content.style.display = 'none';
            });

            if (tabName === 'live') {
                document.getElementById('liveTab').style.display = 'block';
                this.currentTab = 'live';
            } else if (tabName === 'history') {
                document.getElementById('historyTab').style.display = 'block';
                this.currentTab = 'history';
                this.fetchHistory();
            }
        };

        window.showTooltip = (event) => {
            event.stopPropagation();
            const tooltip = event.target.nextElementSibling;
            if (tooltip && tooltip.classList.contains('tooltip-text')) {
                if (tooltip.style.visibility === 'visible') {
                    tooltip.style.visibility = 'hidden';
                    tooltip.style.opacity = '0';
                } else {
                    document.querySelectorAll('.tooltip-text').forEach(t => {
                        t.style.visibility = 'hidden';
                        t.style.opacity = '0';
                    });

                    tooltip.style.visibility = 'visible';
                    tooltip.style.opacity = '1';

                    setTimeout(() => {
                        tooltip.style.visibility = 'hidden';
                        tooltip.style.opacity = '0';
                    }, 5000);
                }
            }
        };

        document.addEventListener('click', (event) => {
            if (!event.target.classList.contains('info-icon')) {
                document.querySelectorAll('.tooltip-text').forEach(tooltip => {
                    tooltip.style.visibility = 'hidden';
                    tooltip.style.opacity = '0';
                });
            }
        });

        this.updateConnectionStatus('connecting');
        this.updateMatchCount(0);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.spikeTracker = new SpikeTracker();
});

document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
        // Page is hidden, don't do anything extra
    } else {
        if (window.spikeTracker && !window.spikeTracker.isConnected) {
            window.spikeTracker.fetchMatches();
        }
    }
});