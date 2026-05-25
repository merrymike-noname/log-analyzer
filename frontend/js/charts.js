/**
 * Chart.js builders for the job details page.
 * All charts share the same minimal aesthetic — solid colors, no gradients,
 * minimal grid lines, consistent typography.
 */

const ChartColors = {
    CRITICAL: '#cf222e',
    HIGH:     '#bc4c00',
    MEDIUM:   '#0969da',
    LOW:      '#1a7f37',
    accent:   '#1f883d',
    muted:    '#59636e',
    grid:     '#d1d9e0',
};

const ChartDefaults = {
    font: {
        family: '-apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        size: 12,
    },
    color: '#1f2328',
};

function applyDefaults() {
    if (!window.Chart) return;
    Chart.defaults.font.family = ChartDefaults.font.family;
    Chart.defaults.font.size = ChartDefaults.font.size;
    Chart.defaults.color = ChartDefaults.color;
    Chart.defaults.plugins.legend.labels.boxWidth = 12;
    Chart.defaults.plugins.legend.labels.padding = 12;
}

function severityDonut(canvas, breakdown) {
    const labels = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
    const data = labels.map(l => breakdown[l] || 0);
    const colors = labels.map(l => ChartColors[l]);

    return new Chart(canvas, {
        type: 'doughnut',
        data: {
            labels,
            datasets: [{
                data,
                backgroundColor: colors,
                borderColor: '#ffffff',
                borderWidth: 2,
            }],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: '60%',
            plugins: {
                legend: {
                    position: 'right',
                    labels: { usePointStyle: true, pointStyle: 'circle' },
                },
                tooltip: {
                    callbacks: {
                        label: (ctx) => {
                            const total = ctx.dataset.data.reduce((a, b) => a + b, 0);
                            const pct = total ? ((ctx.parsed / total) * 100).toFixed(1) : 0;
                            return `${ctx.label}: ${ctx.parsed.toLocaleString()} (${pct}%)`;
                        },
                    },
                },
            },
        },
    });
}

function topTemplatesBar(canvas, templates) {
    // Trim very long template strings for the y-axis label
    const labels = templates.map(t => {
        const text = t.template || t.templateId;
        return text.length > 50 ? text.substring(0, 50) + '...' : text;
    });
    const data = templates.map(t => t.count);

    return new Chart(canvas, {
        type: 'bar',
        data: {
            labels,
            datasets: [{
                data,
                backgroundColor: ChartColors.accent,
                borderRadius: 3,
            }],
        },
        options: {
            indexAxis: 'y',
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        title: (items) => templates[items[0].dataIndex].template || '',
                        label: (ctx) => `${ctx.parsed.x.toLocaleString()} entries`,
                    },
                },
            },
            scales: {
                x: {
                    grid: { color: ChartColors.grid, drawBorder: false },
                    ticks: { color: ChartColors.muted },
                },
                y: {
                    grid: { display: false },
                    ticks: {
                        color: ChartColors.muted,
                        font: { family: 'ui-monospace, monospace', size: 11 },
                    },
                },
            },
        },
    });
}

function scoreDistributionBar(canvas, dist) {
    const labels = ['Min', 'Median', 'P95', 'P99', 'Max'];
    const data = [dist.min, dist.median, dist.p95, dist.p99, dist.max];

    return new Chart(canvas, {
        type: 'bar',
        data: {
            labels,
            datasets: [{
                data,
                backgroundColor: [
                    ChartColors.LOW,
                    ChartColors.LOW,
                    ChartColors.MEDIUM,
                    ChartColors.HIGH,
                    ChartColors.CRITICAL,
                ],
                borderRadius: 3,
            }],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: { display: false },
                tooltip: {
                    callbacks: {
                        label: (ctx) => ctx.parsed.y.toFixed(4),
                    },
                },
            },
            scales: {
                x: {
                    grid: { display: false },
                    ticks: { color: ChartColors.muted },
                },
                y: {
                    grid: { color: ChartColors.grid, drawBorder: false },
                    ticks: { color: ChartColors.muted },
                    beginAtZero: true,
                    max: 1.0,
                },
            },
        },
    });
}

window.Charts = {
    applyDefaults,
    severityDonut,
    topTemplatesBar,
    scoreDistributionBar,
};