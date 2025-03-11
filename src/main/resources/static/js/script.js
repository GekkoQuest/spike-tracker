function refreshMatches() {
    console.log("Fetching matches..."); // Debugging log

    fetch('/matches')
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! Status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log("Matches fetched successfully:", data);

            const matchesContainer = document.getElementById('matches-container');
            const loading = document.getElementById('loading');

            loading.style.display = 'none';
            matchesContainer.style.display = 'block';

            matchesContainer.innerHTML = '';

            if (data.length > 0) {
                data.forEach(match => {
                    const messageDiv = document.createElement('div');
                    messageDiv.className = 'message';
                    messageDiv.innerHTML = `<strong>${match.team1} vs ${match.team2}</strong><br><span>${match.score1} - ${match.score2}</span>`;
                    matchesContainer.appendChild(messageDiv);

                    const streamLink = matchStreamLinks[match.match_page];
                    if (streamLink) {
                        const streamLinkDiv = document.createElement('div');
                        streamLinkDiv.className = 'stream-link';
                        streamLinkDiv.innerHTML = `<a href="${streamLink}" target="_blank">Watch Live Stream</a>`;
                        matchesContainer.appendChild(streamLinkDiv);
                    }
                });
            } else {
                const noMatchesDiv = document.createElement('div');
                noMatchesDiv.className = 'no-matches';
                noMatchesDiv.textContent = 'No current matches.';
                matchesContainer.appendChild(noMatchesDiv);
            }

            const lastCheckedTime = document.getElementById('lastCheckedTime');
            const now = new Date();
            lastCheckedTime.textContent = now.toLocaleTimeString();
        })
        .catch(error => {
            console.error("Error fetching matches:", error); // Debugging log

            // Display an error message
            const matchesContainer = document.getElementById('matches-container');
            const loading = document.getElementById('loading');

            loading.style.display = 'none';
            matchesContainer.style.display = 'block';
            matchesContainer.innerHTML = '<div class="no-matches">Failed to load matches. Please try again later.</div>';
        });
}

refreshMatches();

setInterval(refreshMatches, 5000);