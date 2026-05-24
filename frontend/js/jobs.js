/**
 * Jobs list page: list, upload, polling of in-progress jobs.
 */
(function () {
    if (!Auth.isAuthenticated()) {
        window.location.href = 'index.html';
        return;
    }

    const PAGE_SIZE = 20;
    const POLL_INTERVAL_MS = 3000;

    const tbody = document.getElementById('jobsTbody');
    const emptyState = document.getElementById('emptyState');
    const uploadZone = document.getElementById('uploadZone');
    const fileInput = document.getElementById('fileInput');
    const uploadProgress = document.getElementById('uploadProgress');
    const logoutBtn = document.getElementById('logoutBtn');
    const userInfo = document.getElementById('userInfo');

    let pollTimer = null;

    init();

    async function init() {
        try {
            const me = await api.request('/users/me');
            userInfo.textContent = me.email;
        } catch { /* if /me fails the redirect already happened */ }

        bindUpload();
        bindLogout();
        await refreshJobs();
        schedulePolling();
    }

    function bindLogout() {
        logoutBtn.addEventListener('click', async (e) => {
            e.preventDefault();
            await Auth.logout();
            window.location.href = 'index.html';
        });
    }

    function bindUpload() {
        uploadZone.addEventListener('click', () => fileInput.click());

        ['dragenter', 'dragover'].forEach(evt =>
            uploadZone.addEventListener(evt, (e) => {
                e.preventDefault();
                uploadZone.classList.add('dragover');
            })
        );
        ['dragleave', 'drop'].forEach(evt =>
            uploadZone.addEventListener(evt, (e) => {
                e.preventDefault();
                uploadZone.classList.remove('dragover');
            })
        );

        uploadZone.addEventListener('drop', (e) => {
            const file = e.dataTransfer.files[0];
            if (file) uploadFile(file);
        });

        fileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) uploadFile(file);
            fileInput.value = ''; // allow re-uploading same file
        });
    }

    async function uploadFile(file) {
        const allowed = /\.(log|txt)$/i;
        if (!allowed.test(file.name)) {
            uploadProgress.textContent = 'Only .log or .txt files are accepted';
            return;
        }

        const formData = new FormData();
        formData.append('file', file);

        uploadProgress.textContent = `Uploading ${file.name}...`;

        try {
            const job = await api.request('/jobs', { method: 'POST', body: formData });
            uploadProgress.textContent = `Uploaded ${job.originalFilename} — queued for analysis`;
            await refreshJobs();
        } catch (err) {
            uploadProgress.textContent = `Upload failed: ${err.message}`;
        }
    }

    async function refreshJobs() {
        try {
            const page = await api.request(`/jobs?page=0&size=${PAGE_SIZE}`);
            renderJobs(page.content);
        } catch (err) {
            console.error('Failed to load jobs', err);
        }
    }

    function renderJobs(jobs) {
        if (jobs.length === 0) {
            tbody.innerHTML = '';
            emptyState.style.display = '';
            return;
        }
        emptyState.style.display = 'none';

        tbody.innerHTML = jobs.map(job => {
            const statusClass = `status--${job.status.toLowerCase()}`;
            const lines = job.lineCount != null ? job.lineCount.toLocaleString() : '—';
            const created = formatDate(job.createdAt);
            const canOpen = job.status === 'DONE' || job.status === 'FAILED';
            const shortId = job.id.split('-')[0];
            return `
            <tr class="${canOpen ? 'clickable' : ''}" data-id="${job.id}">
                <td class="mono">${shortId}</td>
                <td>${escapeHtml(job.originalFilename)}</td>
                <td><span class="status ${statusClass}">${job.status}</span></td>
                <td>${lines}</td>
                <td class="text-muted">${created}</td>
            </tr>
        `;
        }).join('');

        tbody.querySelectorAll('tr.clickable').forEach(row =>
            row.addEventListener('click', () => {
                window.location.href = `job.html?id=${row.dataset.id}`;
            })
        );
    }

    function schedulePolling() {
        if (pollTimer) clearInterval(pollTimer);
        pollTimer = setInterval(async () => {
            // Only poll while the page is active
            if (document.hidden) return;
            const hasInProgress = Array.from(
                tbody.querySelectorAll('.status')
            ).some(el =>
                el.classList.contains('status--queued')
                || el.classList.contains('status--processing')
            );
            if (hasInProgress) await refreshJobs();
        }, POLL_INTERVAL_MS);
    }

    function formatDate(iso) {
        if (!iso) return '';
        const d = new Date(iso);
        return d.toLocaleString([], { year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit' });
    }

    function escapeHtml(s) {
        const div = document.createElement('div');
        div.textContent = s || '';
        return div.innerHTML;
    }
})();