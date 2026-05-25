/**
 * Job details page: header, statistics, log table with filters/sort/pagination.
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

    const PAGE_SIZE = 100;
    const SEVERITY_VALUES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];

    const content = document.getElementById('jobContent');
    const userInfo = document.getElementById('userInfo');
    const logoutBtn = document.getElementById('logoutBtn');

    // Filter / sort / paging state
    const state = {
        severity: new Set(),       // empty = no filter
        component: '',             // '' = no filter
        search: '',
        sortBy: 'lineId',
        sortDir: 'asc',
        page: 0,
    };

    init();

    async function init() {
        bindLogout();
        try {
            const me = await api.request('/users/me');
            userInfo.textContent = me.email;
        } catch { /* redirect handled */ }

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
            await renderStatistics();
            renderLogsSection();
            await loadLogs();
        } else if (job.status === 'FAILED') {
            renderFailure(job);
        } else {
            renderInProgress();
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

    async function renderStatistics() {
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

    function renderLogsSection() {
        const body = document.getElementById('jobBody');
        body.insertAdjacentHTML('beforeend', `
            <div class="card">
                <h2>Log entries</h2>

                <div class="filters">
                    <div class="filter-group">
                        <label>Severity</label>
                        <div class="toggle-group" id="severityToggles">
                            ${SEVERITY_VALUES.map(s =>
            `<button class="toggle" data-sev="${s}">${s}</button>`
        ).join('')}
                        </div>
                    </div>

                    <div class="filter-group">
                        <label for="componentSelect">Component</label>
                        <select id="componentSelect">
                            <option value="">All</option>
                            <option value="CBS">CBS</option>
                            <option value="CSI">CSI</option>
                        </select>
                    </div>

                    <div class="filter-group">
                        <label for="searchInput">Search</label>
                        <input type="text" id="searchInput" placeholder="Substring in message...">
                        <button class="btn btn--outline" id="searchApply">Apply</button>
                    </div>
                </div>

                <div class="table-wrapper">
                    <table class="logs-table">
                        <thead>
                            <tr>
                                <th class="col-line sortable" data-sort="lineId">Line<span class="sort-arrow"></span></th>
                                <th class="col-time sortable" data-sort="timestamp">Date/Time<span class="sort-arrow"></span></th>
                                <th class="col-component">Comp</th>
                                <th class="col-severity sortable" data-sort="severity">Severity<span class="sort-arrow"></span></th>
                                <th class="col-score sortable" data-sort="anomalyScore">Score<span class="sort-arrow"></span></th>
                                <th class="col-message">Message</th>
                            </tr>
                        </thead>
                        <tbody id="logsTbody"></tbody>
                    </table>
                    <div class="pagination" id="pagination"></div>
                </div>
            </div>
        `);

        bindFilters();
    }

    function bindFilters() {
        // Severity toggles
        document.querySelectorAll('#severityToggles .toggle').forEach(btn => {
            btn.addEventListener('click', () => {
                const sev = btn.dataset.sev;
                if (state.severity.has(sev)) {
                    state.severity.delete(sev);
                    btn.classList.remove('active');
                } else {
                    state.severity.add(sev);
                    btn.classList.add('active');
                }
                state.page = 0;
                loadLogs();
            });
        });

        // Component
        document.getElementById('componentSelect').addEventListener('change', (e) => {
            state.component = e.target.value;
            state.page = 0;
            loadLogs();
        });

        // Search
        const searchInput = document.getElementById('searchInput');
        const applySearch = () => {
            state.search = searchInput.value.trim();
            state.page = 0;
            loadLogs();
        };
        document.getElementById('searchApply').addEventListener('click', applySearch);
        searchInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') applySearch();
        });

        // Column sort
        document.querySelectorAll('.logs-table .sortable').forEach(th => {
            th.addEventListener('click', () => {
                const field = th.dataset.sort;
                if (state.sortBy === field) {
                    state.sortDir = state.sortDir === 'asc' ? 'desc' : 'asc';
                } else {
                    state.sortBy = field;
                    state.sortDir = 'asc';
                }
                state.page = 0;
                loadLogs();
            });
        });
    }

    async function loadLogs() {
        const tbody = document.getElementById('logsTbody');
        tbody.innerHTML = `<tr><td colspan="6" class="empty-state">Loading...</td></tr>`;

        const params = new URLSearchParams({
            page: state.page,
            size: PAGE_SIZE,
            sortBy: state.sortBy,
            sortDir: state.sortDir,
        });
        if (state.severity.size > 0) params.set('severity', [...state.severity].join(','));
        if (state.component) params.set('component', state.component);
        if (state.search) params.set('search', state.search);

        let page;
        try {
            page = await api.request(`/jobs/${jobId}/logs?${params}`);
        } catch (err) {
            tbody.innerHTML = `<tr><td colspan="6" class="empty-state">
                Failed to load: ${escapeHtml(err.message)}
            </td></tr>`;
            return;
        }

        renderLogs(page);
        renderPagination(page);
        renderSortArrows();
    }

    function renderLogs(page) {
        const tbody = document.getElementById('logsTbody');
        if (page.content.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="empty-state">No entries match the filters.</td></tr>`;
            return;
        }
        tbody.innerHTML = page.content.map(entry => {
            const time = formatTimestamp(entry.timestamp);
            const score = entry.anomalyScore != null ? entry.anomalyScore.toFixed(3) : '';
            const sev = entry.severity || 'LOW';
            return `
            <tr class="sev-${sev}">
                <td class="col-line">${entry.lineId}</td>
                <td class="col-time">${time}</td>
                <td class="col-component">${escapeHtml(entry.component || '')}</td>
                <td class="col-severity">
                    <span class="severity severity--${sev}">${sev}</span>
                </td>
                <td class="col-score">${score}</td>
                <td class="col-message" title="${escapeHtml(entry.content || '')}">
                    ${escapeHtml(entry.content || '')}
                </td>
            </tr>
        `;
        }).join('');

        tbody.querySelectorAll('tr').forEach(row =>
            row.addEventListener('click', () => row.classList.toggle('expanded'))
        );
    }

    function renderPagination(page) {
        const el = document.getElementById('pagination');
        const totalPages = page.totalPages || 1;
        const current = page.page + 1;
        const prevDisabled = page.page === 0 ? 'disabled' : '';
        const nextDisabled = current >= totalPages ? 'disabled' : '';

        el.innerHTML = `
        <button id="prevPage" ${prevDisabled}>Previous</button>
        <span>Page ${current} of ${totalPages} · ${page.totalElements.toLocaleString()} total</span>
        <button id="nextPage" ${nextDisabled}>Next</button>
        <span class="page-jump">
            <span>Go to</span>
            <input type="number" id="pageJumpInput" min="1" max="${totalPages}" placeholder="${current}">
        </span>
    `;

        document.getElementById('prevPage').addEventListener('click', () => {
            if (state.page > 0) { state.page--; loadLogs(); }
        });
        document.getElementById('nextPage').addEventListener('click', () => {
            if (state.page + 1 < totalPages) { state.page++; loadLogs(); }
        });

        const jumpInput = document.getElementById('pageJumpInput');
        const jumpTo = () => {
            const target = parseInt(jumpInput.value, 10);
            if (isNaN(target) || target < 1 || target > totalPages) {
                jumpInput.value = '';
                return;
            }
            state.page = target - 1;
            loadLogs();
        };
        jumpInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') jumpTo();
        });
        jumpInput.addEventListener('blur', () => {
            if (jumpInput.value) jumpTo();
        });
    }

    function renderSortArrows() {
        document.querySelectorAll('.logs-table .sortable').forEach(th => {
            const arrow = th.querySelector('.sort-arrow');
            if (th.dataset.sort === state.sortBy) {
                th.classList.add('sorted');
                arrow.textContent = state.sortDir === 'asc' ? '▲' : '▼';
            } else {
                th.classList.remove('sorted');
                arrow.textContent = '';
            }
        });
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
                    Analysis did not complete. You can delete this job and try uploading the file again.
                </p>
            </div>
        `;
    }

    function renderInProgress() {
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

    function formatTimestamp(iso) {
        if (!iso) return '';
        // iso format: "2016-09-28T04:30:31" -> "09-28 04:30:31"
        return iso.substring(5, 10) + ' ' + iso.substring(11, 19);
    }

    function escapeHtml(s) {
        const div = document.createElement('div');
        div.textContent = s || '';
        return div.innerHTML;
    }
})();