function refreshMatches() {
    console.log("Fetching matches...");

    const matchesContainer = document.getElementById('matches-container');
    const loading = document.getElementById('loading');
    const lastCheckedTime = document.getElementById('lastCheckedTime');

    fetch('/api/matches')
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! Status: ${response.status}`);
            }
            return response.json();
        })
        .then(matches => {
            matchesContainer.innerHTML = '';
            loading.style.display = 'none';
            matchesContainer.style.display = 'block';

            if (matches.length > 0) {
                matches.forEach(match => {
                    const matchDiv = document.createElement('div');
                    matchDiv.className = 'message';
                    matchDiv.innerHTML = `
                        <strong>${match.team1} vs ${match.team2}</strong><br>
                        <span>Score: ${match.score1} - ${match.score2}</span>
                    `;

                    if (match.streamLink) {
                        const streamLinkDiv = document.createElement('div');
                        streamLinkDiv.className = 'stream-link';
                        streamLinkDiv.innerHTML = `<a href="${match.streamLink}" target="_blank">🎥 Watch Live Stream</a>`;
                        matchDiv.appendChild(streamLinkDiv);
                    }

                    matchesContainer.appendChild(matchDiv);
                });
            } else {
                const noMatchesDiv = document.createElement('div');
                noMatchesDiv.className = 'no-matches';
                noMatchesDiv.textContent = 'No current matches.';
                matchesContainer.appendChild(noMatchesDiv);
            }

            const now = new Date();
            lastCheckedTime.textContent = now.toLocaleTimeString();
            console.log("Matches fetched successfully.");
        })
        .catch(error => {
            console.error("Error fetching matches:", error);
            loading.style.display = 'none';
            matchesContainer.style.display = 'block';

            matchesContainer.innerHTML = `
                <div class="no-matches">
                    ❌ Failed to load matches. Please try again later.
                </div>`;

            const now = new Date();
            lastCheckedTime.textContent = now.toLocaleTimeString();
        });
}

refreshMatches();
setInterval(refreshMatches, 5000);