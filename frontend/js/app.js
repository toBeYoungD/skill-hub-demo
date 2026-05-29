// API基础配置
const API_BASE_URL = 'http://localhost:8080/api';

function getUserId() {
    return document.getElementById('user-id-input').value || 'demo-user';
}

async function apiRequest(endpoint, options = {}) {
    try {
        const isFormData = options.body instanceof FormData;
        const config = { ...options };
        if (!isFormData) {
            config.headers = { 'Content-Type': 'application/json', 'X-User-Id': getUserId(), ...options.headers };
        }
        const response = await fetch(`${API_BASE_URL}${endpoint}`, config);
        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || '请求失败');
        }
        return await response.json();
    } catch (error) {
        console.error('API请求失败:', error);
        throw error;
    }
}

function formatDate(dateString) {
    return new Date(dateString).toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function getLatestVersion(skill) {
    if (!skill.versions || skill.versions.length === 0) return '无';
    const latest = skill.versions.find(v => v.isLatest);
    return latest ? latest.version : skill.versions[skill.versions.length - 1].version;
}

function getStatusBadge(status) {
    const map = {
        'DRAFT': '<span class="status-badge status-draft">草稿</span>',
        'PENDING_REVIEW': '<span class="status-badge status-pending">审批中</span>',
        'PUBLISHED': '<span class="status-badge status-published">已发布</span>',
        'REJECTED': '<span class="status-badge status-rejected">未通过</span>',
        'DELISTED': '<span class="status-badge status-delisted">已下架</span>'
    };
    return map[status] || status;
}

function showToast(message, type = 'success') {
    const el = document.getElementById('notify-toast');
    el.className = `alert alert-${type}`;
    el.textContent = message;
    el.style.display = 'block';
    setTimeout(() => el.style.display = 'none', 3000);
}

// Modal
const modal = document.getElementById('modal');
const modalBody = document.getElementById('modal-body');
document.querySelector('.close').addEventListener('click', () => modal.style.display = 'none');
window.addEventListener('click', e => { if (e.target === modal) modal.style.display = 'none'; });
function showModal(content) { modalBody.innerHTML = content; modal.style.display = 'block'; }
function hideModal() { modal.style.display = 'none'; }

// 视图切换
document.querySelectorAll('.nav-btn[data-view]').forEach(btn => {
    btn.addEventListener('click', () => switchView(btn.dataset.view));
});

function switchView(viewName) {
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    document.getElementById(`${viewName}-view`).classList.add('active');
    document.querySelectorAll('.nav-btn[data-view]').forEach(b => b.classList.remove('active'));
    document.querySelector(`.nav-btn[data-view="${viewName}"]`).classList.add('active');
    if (viewName === 'home') loadHomeStats();
    else if (viewName === 'skills') loadSkills();
    else if (viewName === 'admin') loadReviews();
}

// 首页统计
async function loadHomeStats() {
    try {
        const skillsResp = await apiRequest('/skills');
        const skills = skillsResp.data;
        let published = 0, pending = 0;
        skills.forEach(s => {
            if (s.status === 'PUBLISHED') published++;
            if (s.status === 'PENDING_REVIEW') pending++;
        });
        document.getElementById('total-skills').textContent = skills.length;
        document.getElementById('published-skills').textContent = published;
        document.getElementById('pending-reviews').textContent = pending;
    } catch (e) { console.error(e); }
}

// 技能管理
async function loadSkills() {
    const list = document.getElementById('skills-list');
    list.innerHTML = '<div class="loading"><div class="spinner"></div></div>';
    try {
        const resp = await apiRequest('/skills');
        const skills = resp.data;
        if (!skills.length) {
            list.innerHTML = '<div class="empty-state"><h3>暂无技能</h3><p>点击"创建技能"开始</p></div>';
            return;
        }
        list.innerHTML = skills.map(skill => `
            <div class="skill-card">
                ${getStatusBadge(skill.status)}
                <h3>${skill.name}</h3>
                <p>${skill.description || '暂无描述'}</p>
                <div class="skill-info">
                    <p><strong>开发者:</strong> ${skill.developer || '-'}</p>
                    <p><strong>版本:</strong> ${getLatestVersion(skill)}</p>
                    <p><strong>下载:</strong> ${skill.downloadCount || 0} | <strong>使用:</strong> ${skill.useCount || 0}</p>
                    <p><strong>创建:</strong> ${formatDate(skill.createdAt)}</p>
                </div>
                <div class="skill-actions">
                    <button class="btn btn-primary" onclick="viewSkill(${skill.id})">查看</button>
                    <button class="btn btn-secondary" onclick="editSkill(${skill.id})">编辑</button>
                    ${skill.status !== 'DELISTED' ? `<button class="btn btn-info btn-sm" onclick="saveSkill(${skill.id})">保存</button>` : ''}
                    ${skill.canPublish ? `<button class="btn btn-success" onclick="publishSkill(${skill.id})">发布</button>` : ''}
                    ${(skill.status === 'DRAFT' || skill.status === 'REJECTED') && !skill.lastPublishedAt ? `<button class="btn btn-danger" onclick="deleteSkill(${skill.id})">删除</button>` : ''}
                    <button class="btn btn-warning btn-sm" onclick="exportSkill(${skill.id})">导出</button>
                </div>
            </div>
        `).join('');
    } catch (e) { list.innerHTML = `<div class="empty-state"><h3>加载失败</h3><p>${e.message}</p></div>`; }
}

// 创建技能表单
function showCreateSkillForm() {
    showModal(`
        <h2>创建技能</h2>
        <div id="skill-form-alert"></div>
        <form id="create-skill-form">
            <div class="form-group"><label>技能名称*</label><input type="text" name="name" required></div>
            <div class="form-group"><label>描述*</label><textarea name="description" required></textarea></div>
            <div class="form-group"><label>开发者*</label><input type="text" name="developer" required></div>
            <div class="form-group"><label>可见范围</label>
                <select name="visibilityType">
                    <option value="PUBLIC">全公司</option>
                    <option value="USER_LIST">指定人员</option>
                </select>
            </div>
            <div class="form-group"><label>技能包</label><input type="file" name="packageFile" accept=".zip"></div>
            <button type="submit" class="btn btn-primary">创建</button>
            <button type="button" class="btn btn-secondary" onclick="hideModal()">取消</button>
        </form>
    `);
}

document.addEventListener('submit', async (e) => {
    if (e.target.id === 'create-skill-form') {
        e.preventDefault();
        const fd = new FormData(e.target);
        fd.append('version', '1');
        const alertDiv = document.getElementById('skill-form-alert');
        try {
            const resp = await apiRequest('/skills', { method: 'POST', body: fd });
            if (resp.success) { showToast('技能创建成功'); hideModal(); loadSkills(); }
            else alertDiv.innerHTML = `<div class="alert alert-error">${resp.message}</div>`;
        } catch (err) { alertDiv.innerHTML = `<div class="alert alert-error">${err.message}</div>`; }
    }

    if (e.target.id === 'edit-skill-form') {
        e.preventDefault();
        const form = e.target;
        const name = form.querySelector('[name="name"]').value;
        const description = form.querySelector('[name="description"]').value;
        const packageFile = form.querySelector('[name="packageFile"]').files[0];
        const skillId = window.currentEditingSkillId;
        const alertDiv = document.getElementById('edit-skill-alert');

        try {
            let response;
            if (packageFile && packageFile.size > 0) {
                const fd = new FormData();
                if (name) fd.append('name', name);
                if (description) fd.append('description', description);
                fd.append('packageFile', packageFile);
                response = await fetch(`${API_BASE_URL}/skills/${skillId}`, { method: 'PUT', body: fd, headers: { 'X-User-Id': getUserId() } });
            } else {
                response = await fetch(`${API_BASE_URL}/skills/${skillId}`, {
                    method: 'PUT', headers: { 'Content-Type': 'application/json', 'X-User-Id': getUserId() },
                    body: JSON.stringify({ name, description })
                });
            }
            if (!response.ok) throw new Error(await response.text());
            const data = await response.json();
            if (data.success) { showToast('技能更新成功'); hideModal(); loadSkills(); }
            else alertDiv.innerHTML = `<div class="alert alert-error">${data.message}</div>`;
        } catch (err) { alertDiv.innerHTML = `<div class="alert alert-error">${err.message}</div>`; }
    }
});

// 查看技能详情
async function viewSkill(skillId) {
    try {
        const resp = await apiRequest(`/skills/${skillId}`);
        const skill = resp.data;
        showModal(`
            <h2>${skill.name}</h2>
            <p>${skill.description || '暂无描述'} | ${getStatusBadge(skill.status)}</p>
            <p><strong>开发者:</strong> ${skill.developer} | <strong>下载:</strong> ${skill.downloadCount} | <strong>使用:</strong> ${skill.useCount}</p>
            <h3>版本历史 (${skill.versions.length})</h3>
            ${skill.versions.length ? skill.versions.slice().reverse().map(v => `
                <div class="version-card ${v.isLatest ? 'latest' : ''} ${v.isRollback ? 'rollback' : ''}">
                    <div class="version-header">
                        <strong>v${v.version}</strong>
                        ${v.isLatest ? '<span class="badge badge-latest">最新</span>' : ''}
                        ${v.isRollback ? '<span class="badge badge-rollback">回滚</span>' : ''}
                    </div>
                    <p>${v.changelog || '无更新日志'} | ${formatDate(v.createdAt)}</p>
                    <div class="version-actions">
                        ${v.status === 'PUBLISHED' && !v.isLatest ? `<button class="btn btn-warning btn-sm" onclick="rollbackVersion(${skill.id},${v.id})">回滚</button>` : ''}
                        ${!v.isLatest ? `<button class="btn btn-danger btn-sm" onclick="deleteVersion(${skill.id},${v.id})">删除</button>` : ''}
                    </div>
                </div>
            `).join('') : '<p>暂无版本</p>'}
            <button class="btn btn-secondary" onclick="hideModal()">关闭</button>
        `);
    } catch (e) { showToast('获取详情失败', 'error'); }
}

// 编辑技能
async function editSkill(skillId) {
    try {
        const resp = await apiRequest(`/skills/${skillId}`);
        const skill = resp.data;
        window.currentEditingSkillId = skillId;
        showModal(`
            <h2>编辑技能</h2>
            <div id="edit-skill-alert"></div>
            <form id="edit-skill-form">
                <div class="form-group"><label>名称*</label><input type="text" name="name" value="${skill.name}" required></div>
                <div class="form-group"><label>描述*</label><textarea name="description" required>${skill.description || ''}</textarea></div>
                <div class="form-group"><label>技能包</label><input type="file" name="packageFile" accept=".zip">
                ${skill.packageUrl ? `<p>当前文件: ${skill.packageUrl}</p>` : ''}</div>
                <button type="submit" class="btn btn-primary">更新</button>
                <button type="button" class="btn btn-secondary" onclick="hideModal()">取消</button>
            </form>
        `);
    } catch (e) { showToast('获取技能信息失败', 'error'); }
}

// 发布技能（提交审批）
async function publishSkill(skillId) {
    showModal(`
        <h2>提交审批</h2>
        <div id="publish-alert"></div>
        <form id="publish-skill-form">
            <div class="form-group"><label>更新日志</label><textarea name="changelog" placeholder="描述本次更新内容..."></textarea></div>
            <button type="submit" class="btn btn-primary">提交</button>
            <button type="button" class="btn btn-secondary" onclick="hideModal()">取消</button>
        </form>
    `);
    document.getElementById('publish-skill-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const fd = new FormData(e.target);
        const alertDiv = document.getElementById('publish-alert');
        try {
            const resp = await apiRequest(`/skills/${skillId}/publish`, { method: 'POST', body: fd });
            if (resp.success) { showToast(resp.message); hideModal(); loadSkills(); }
            else alertDiv.innerHTML = `<div class="alert alert-error">${resp.message}</div>`;
        } catch (err) { alertDiv.innerHTML = `<div class="alert alert-error">${err.message}</div>`; }
    });
}

// 删除技能
async function deleteSkill(skillId) {
    if (!confirm('确定删除此技能？仅草稿/未通过状态可删。')) return;
    try {
        await apiRequest(`/skills/${skillId}`, { method: 'DELETE' });
        showToast('技能已删除'); loadSkills();
    } catch (e) { showToast(e.message, 'error'); }
}

// 保存为草稿
async function saveSkill(skillId) {
    try {
        await apiRequest(`/skills/${skillId}/save`, { method: 'POST' });
        showToast('已保存为草稿');
        loadSkills();
    } catch (e) { showToast(e.message, 'error'); }
}

// 导出
function exportSkill(skillId) { window.open(`${API_BASE_URL}/skills/${skillId}/export`, '_blank'); }

// 版本操作
async function rollbackVersion(skillId, versionId) {
    if (!confirm('确定回滚到此版本？')) return;
    try {
        const resp = await apiRequest(`/skills/${skillId}/versions/${versionId}/rollback`, { method: 'POST' });
        showToast(resp.message); hideModal(); loadSkills();
    } catch (e) { showToast(e.message, 'error'); }
}

async function deleteVersion(skillId, versionId) {
    if (!confirm('确定删除此版本？')) return;
    try {
        await apiRequest(`/skills/${skillId}/versions/${versionId}`, { method: 'DELETE' });
        showToast('版本已删除'); hideModal(); loadSkills();
    } catch (e) { showToast(e.message, 'error'); }
}

// 管理台
function switchAdminTab(tab) {
    document.querySelectorAll('.admin-panel').forEach(p => p.classList.remove('active'));
    document.getElementById(`admin-${tab}-panel`).classList.add('active');
    document.querySelectorAll('#admin-view .nav-btn').forEach(b => b.classList.remove('active'));
    event.target.classList.add('active');
    if (tab === 'reviews') loadReviews(); else loadAdminSkills();
}

async function loadReviews() {
    const list = document.getElementById('reviews-list');
    list.innerHTML = '<div class="loading"><div class="spinner"></div></div>';
    try {
        const status = document.getElementById('review-status-filter').value;
        const endpoint = status ? `/admin/reviews?status=${status}` : '/admin/reviews';
        const resp = await apiRequest(endpoint);
        const reviews = resp.data;
        if (!reviews.length) { list.innerHTML = '<div class="empty-state"><h3>暂无审批记录</h3></div>'; return; }
        list.innerHTML = reviews.map(r => `
            <div class="review-card">
                <div class="review-header">
                    <strong>${r.skillName}</strong>
                    <span class="badge ${r.status === 'PENDING' ? 'badge-pending' : r.status === 'APPROVED' ? 'badge-approved' : 'badge-rejected'}">${r.status}</span>
                </div>
                <p>申请人: ${r.applicant} | 时间: ${formatDate(r.createdAt)}</p>
                ${r.changelog ? `<p>更新: ${r.changelog}</p>` : ''}
                ${r.rejectReason ? `<p style="color:red">原因: ${r.rejectReason}</p>` : ''}
                ${r.status === 'PENDING' ? `
                    <div class="review-actions">
                        <button class="btn btn-success btn-sm" onclick="approveReview(${r.id})">通过</button>
                        <button class="btn btn-danger btn-sm" onclick="rejectReview(${r.id})">拒绝</button>
                    </div>
                ` : ''}
            </div>
        `).join('');
    } catch (e) { list.innerHTML = `<div class="empty-state"><h3>加载失败</h3></div>`; }
}

async function approveReview(id) {
    try { await apiRequest(`/admin/reviews/${id}/approve`, { method: 'POST' }); showToast('已通过'); loadReviews(); loadSkills(); }
    catch (e) { showToast(e.message, 'error'); }
}

async function rejectReview(id) {
    const reason = prompt('请输入拒绝理由:');
    if (!reason) return;
    try { await apiRequest(`/admin/reviews/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }); showToast('已拒绝'); loadReviews(); loadSkills(); }
    catch (e) { showToast(e.message, 'error'); }
}

async function loadAdminSkills() {
    const list = document.getElementById('admin-skills-list');
    list.innerHTML = '<div class="loading"><div class="spinner"></div></div>';
    try {
        const resp = await apiRequest('/skills');
        const skills = resp.data;
        list.innerHTML = skills.map(skill => `
            <div class="review-card">
                <div class="review-header">
                    <strong>${skill.name}</strong>
                    ${getStatusBadge(skill.status)}
                </div>
                <p>开发者: ${skill.developer || '-'} | 版本: ${getLatestVersion(skill)} | 创建: ${formatDate(skill.createdAt)}</p>
                <div class="review-actions">
                    ${skill.status === 'PUBLISHED' ? `<button class="btn btn-danger btn-sm" onclick="delistSkill(${skill.id})">下架</button>` : ''}
                    ${skill.status === 'DELISTED' ? `<button class="btn btn-success btn-sm" onclick="restoreSkill(${skill.id})">恢复</button>` : ''}
                </div>
            </div>
        `).join('');
    } catch (e) { list.innerHTML = '<div class="empty-state"><h3>加载失败</h3></div>'; }
}

async function delistSkill(skillId) {
    const reason = prompt('请输入下架原因:');
    if (!reason) return;
    try { await apiRequest(`/admin/skills/${skillId}/delist`, { method: 'POST', body: JSON.stringify({ reason }) }); showToast('已下架'); loadAdminSkills(); loadSkills(); }
    catch (e) { showToast(e.message, 'error'); }
}

async function restoreSkill(skillId) {
    try { await apiRequest(`/admin/skills/${skillId}/restore`, { method: 'POST' }); showToast('已恢复'); loadAdminSkills(); loadSkills(); }
    catch (e) { showToast(e.message, 'error'); }
}

// 搜索
document.getElementById('search-skill-btn').addEventListener('click', async () => {
    const name = document.getElementById('skill-search').value;
    const status = document.getElementById('skill-status-filter').value;
    let endpoint = '/skills?' + (name ? `name=${encodeURIComponent(name)}&` : '') + (status ? `status=${status}` : '');
    const list = document.getElementById('skills-list');
    try {
        const resp = await apiRequest(endpoint);
        const skills = resp.data;
        if (!skills.length) { list.innerHTML = '<div class="empty-state"><h3>无匹配结果</h3></div>'; return; }
        list.innerHTML = skills.map(skill => `
            <div class="skill-card">
                ${getStatusBadge(skill.status)}
                <h3>${skill.name}</h3>
                <p>${skill.description || '暂无描述'}</p>
                <div class="skill-info"><p><strong>开发者:</strong> ${skill.developer}</p><p><strong>版本:</strong> ${getLatestVersion(skill)}</p></div>
                <div class="skill-actions">
                    <button class="btn btn-primary" onclick="viewSkill(${skill.id})">查看</button>
                    <button class="btn btn-secondary" onclick="editSkill(${skill.id})">编辑</button>
                    ${skill.canPublish ? `<button class="btn btn-success" onclick="publishSkill(${skill.id})">发布</button>` : ''}
                    ${(skill.status === 'DRAFT' || skill.status === 'REJECTED') && !skill.lastPublishedAt ? `<button class="btn btn-danger" onclick="deleteSkill(${skill.id})">删除</button>` : ''}
                    <button class="btn btn-warning btn-sm" onclick="exportSkill(${skill.id})">导出</button>
                </div>
            </div>
        `).join('');
    } catch (e) { showToast('搜索失败', 'error'); }
});

// 通知
async function loadNotifications() {
    try {
        const resp = await apiRequest('/notifications');
        const dropdown = document.getElementById('notification-dropdown');
        const notifications = resp.data;
        const unread = notifications.filter(n => !n.read).length;
        const badge = document.getElementById('unread-badge');
        badge.textContent = unread;
        badge.style.display = unread > 0 ? 'inline' : 'none';
        dropdown.innerHTML = notifications.length ? notifications.slice(0, 10).map(n => `
            <div class="notif-item ${n.read ? '' : 'unread'}" onclick="markNotifRead(${n.id})">
                <strong>${n.event}</strong>: ${n.message}<br><small>${formatDate(n.createdAt)}</small>
            </div>
        `).join('') + '<div class="notif-footer"><a href="#" onclick="markAllRead()">全部已读</a></div>' : '<div class="notif-item">暂无通知</div>';
    } catch (e) { }
}

function toggleNotifications() {
    const dropdown = document.getElementById('notification-dropdown');
    dropdown.style.display = dropdown.style.display === 'none' ? 'block' : 'none';
    loadNotifications();
}

async function markNotifRead(id) {
    await apiRequest(`/notifications/${id}/read`, { method: 'POST' });
    loadNotifications();
}

async function markAllRead() {
    await apiRequest('/notifications/read-all', { method: 'POST' });
    loadNotifications();
}

// 按钮绑定
document.getElementById('create-skill-btn').addEventListener('click', showCreateSkillForm);

// 初始化
document.addEventListener('DOMContentLoaded', () => { loadHomeStats(); loadNotifications(); });
setInterval(loadNotifications, 30000);