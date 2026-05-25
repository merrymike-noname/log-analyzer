/**
 * Job details page (caркас).
 * Stages: load job → render header + actions → if DONE, load statistics.
 * Table and charts are added in later steps.
 */
(function () {
    if (!Auth.isAuthenticated()) {
        window.location.href = 'index.html';
        return;
    }

    const jobId = new URLSearchParams(window.location.search).get('id');
    if (!jobId) {
        window.location.href = 'jobs.html';
        return;
    }

    const content = document.getElementById('jobContent');
    const userInfo = document.getElementById('userInfo');
    const logoutBtn = document.getElementById('logoutBtn');

    init();

    async function init() {
        bindLogout();
        try {
            const me = await api.request('/users/me');
            userInfo.textContent = me.email;
        } catch { /* redirect handled in api.js */ }

        await loadJob();
    }

    function bindLogout() {
        logoutBtn.addEventListener('click', async (e) => {
            e.preventDefault();
            await Auth.logout();
            window.location.href = 'index.html';
        });
    }

    async function loadJob() {
        let job;
        try {
            job = await api.request(`/jobs/${jobId}`);
        } catch (err) {
            content.innerHTML = `<div class="message-box message-box--error">
                Failed to load job: ${escapeHtml(err.message)}
            </div>`;
            return;
        }

        renderHeader(job);

        if (job.status === 'DONE') {
            await renderStatistics(job);
        } else if (job.status === 'FAILED') {
            renderFailure(job);
        } else {
            renderInProgress(job);
        }
    }

    function renderHeader(job) {
        const created = formatDate(job.createdAt);
        const finished = job.finishedAt ? formatDate(job.finishedAt) : '—';
        const lineCount = job.lineCount != null ? job.lineCount.toLocaleString() : '—';

        content.innerHTML = `
            <div class="card">
                <div class="flex-row">
                    <div>
                        <h1>${escapeHtml(job.originalFilename)}</h1>
                        <div class="job-meta">
                            <span><strong>ID:</strong> <span class="mono">${job.id}</span></span>
                            <span><strong>Status:</strong>
                                <span class="status status--${job.status.toLowerCase()}">${job.status}</span>
                            </span>
                            <span><strong>Lines:</strong> ${lineCount}</span>
                            <span><strong>Uploaded:</strong> ${created}</span>
                            <span><strong>Finished:</strong> ${finished}</span>
                        </div>
                    </div>
                    <div class="spacer"></div>
                    <div class="actions" id="actionsBar"></div>
                </div>
            </div>
            <div id="jobBody"></div>
        `;

        renderActions(job);
    }

    function renderActions(job) {
        const bar = document.getElementById('actionsBar');
        const buttons = [];

        if (job.status === 'DONE') {
            buttons.push(`<a class="btn btn--outline" href="#" id="exportBtn">Export CSV</a>`);
        }
        buttons.push(`<button class="btn btn--danger" id="deleteBtn">Delete</button>`);
        bar.innerHTML = buttons.join('');

        const exportBtn = document.getElementById('exportBtn');
        if (exportBtn) {
            // Export wires up properly in step 7; placeholder href for now
            exportBtn.addEventListener('click', (e) => {
                e.preventDefault();
                alert('Export will be wired up in a later step.');
            });
        }

        document.getElementById('deleteBtn').addEventListener('click', async () => {
            if (!confirm('Delete this job and all its data? This cannot be undone.')) return;
            try {
                await api.request(`/jobs/${jobId}`, { method: 'DELETE' });
                window.location.href = 'jobs.html';
            } catch (err) {
                alert(`Failed to delete: ${err.message}`);
            }
        });
    }

    async function renderStatistics(job) {
        const body = document.getElementById('jobBody');
        body.innerHTML = `<div class="card"><h2>Summary</h2>
            <div class="message-box">Loading statistics...</div></div>`;

        let stats;
        try {
            stats = await api.request(`/jobs/${jobId}/statistics`);
        } catch (err) {
            body.innerHTML = `<div class="card"><h2>Summary</h2>
                <div class="message-box message-box--error">
                    Failed to load statistics: ${escapeHtml(err.message)}
                </div></div>`;
            return;
        }

        const sev = stats.severityBreakdown || {};
        body.innerHTML = `
            <div class="card">
                <h2>Severity breakdown</h2>
                <div class="stats-grid">
                    <div class="stat-card stat-card--critical">
                        <div class="label">Critical</div>
                        <div class="value">${(sev.CRITICAL || 0).toLocaleString()}</div>
                    </div>
                    <div class="stat-card stat-card--high">
                        <div class="label">High</div>
                        <div class="value">${(sev.HIGH || 0).toLocaleString()}</div>
                    </div>
                    <div class="stat-card stat-card--medium">
                        <div class="label">Medium</div>
                        <div class="value">${(sev.MEDIUM || 0).toLocaleString()}</div>
                    </div>
                    <div class="stat-card stat-card--low">
                        <div class="label">Low</div>
                        <div class="value">${(sev.LOW || 0).toLocaleString()}</div>
                    </div>
                </div>
            </div>

            <div class="card">
                <h2>Overview</h2>
                <div class="stats-grid">
                    <div class="stat-card">
                        <div class="label">Total entries</div>
                        <div class="value">${stats.totalEntries.toLocaleString()}</div>
                    </div>
                    <div class="stat-card">
                        <div class="label">Unique templates</div>
                        <div class="value">${stats.uniqueTemplates.toLocaleString()}</div>
                    </div>
                    <div class="stat-card">
                        <div class="label">Median score</div>
                        <div class="value">${formatScore(stats.scoreDistribution?.median)}</div>
                    </div>
                    <div class="stat-card">
                        <div class="label">Max score</div>
                        <div class="value">${formatScore(stats.scoreDistribution?.max)}</div>
                    </div>
                </div>
            </div>
        `;
    }

    function renderFailure(job) {
        const body = document.getElementById('jobBody');
        body.innerHTML = `
            <div class="card">
                <h2>Job failed</h2>
                <div class="alert alert--error">
                    ${escapeHtml(job.errorMessage || 'No error message provided.')}
                </div>
                <p class="text-muted">
                    Analysis has not been completed. You can delete this job and try uploading the file again.
                </p>
            </div>
        `;
    }

    function renderInProgress(job) {
        const body = document.getElementById('jobBody');
        body.innerHTML = `
            <div class="card">
                <div class="message-box">
                    This job is still being processed. Return to the uploads list and wait — the page
                    will update automatically.
                </div>
            </div>
        `;
    }

    function formatScore(s) {
        return s == null ? '—' : s.toFixed(3);
    }

    function formatDate(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        return d.toLocaleString([], {
            year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit'
        });
    }

    function escapeHtml(s) {
        const div = document.createElement('div');
        div.textContent = s || '';
        return div.innerHTML;
    }
})();