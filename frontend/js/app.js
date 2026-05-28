// API基础配置
const API_BASE_URL = 'http://localhost:8080/api';

// 工具函数
async function apiRequest(endpoint, options = {}) {
    try {
        const isFormData = options.body instanceof FormData;

        const config = {
            ...options
        };

        if (!isFormData) {
            config.headers = {
                'Content-Type': 'application/json',
                ...options.headers
            };
        }

        const response = await fetch(`${API_BASE_URL}${endpoint}`, config)
            .catch(err => {
                console.error('Fetch failed:', err.message, endpoint, config.method);
                throw err;
            });

        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || '请求失败');
        }

        const data = await response.json();
        return data;
    } catch (error) {
        console.error('API请求失败:', error);
        throw error;
    }
}

function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function getLatestVersion(skill) {
    if (!skill.versions || skill.versions.length === 0) return '无版本';
    const latest = skill.versions.find(v => v.isLatest);
    return latest ? latest.version : skill.versions[skill.versions.length - 1].version;
}

function getStatusBadge(status) {
    const statusMap = {
        'DRAFT': '<span class="status-badge status-draft">草稿</span>',
        'PUBLISHED': '<span class="status-badge status-published">已发布</span>',
        'ARCHIVED': '<span class="status-badge status-archived">已归档</span>'
    };
    return statusMap[status] || status;
}

function showNotification(message, type = 'success') {
    const notification = document.createElement('div');
    notification.className = `alert alert-${type}`;
    notification.textContent = message;
    notification.style.position = 'fixed';
    notification.style.top = '20px';
    notification.style.right = '20px';
    notification.style.zIndex = '10000';
    notification.style.minWidth = '300px';

    document.body.appendChild(notification);

    setTimeout(() => {
        notification.remove();
    }, 3000);
}

// Modal操作
const modal = document.getElementById('modal');
const modalBody = document.getElementById('modal-body');
const closeBtn = document.querySelector('.close');

function showModal(content) {
    modalBody.innerHTML = content;
    modal.style.display = 'block';
}

function hideModal() {
    modal.style.display = 'none';
}

closeBtn.addEventListener('click', hideModal);

window.addEventListener('click', (e) => {
    if (e.target === modal) {
        hideModal();
    }
});

// 导出技能
function exportSkill(skillId) {
    window.open(`${API_BASE_URL}/skills/${skillId}/export`, '_blank');
}

// 视图切换
function switchView(viewName) {
    // 隐藏所有视图
    document.querySelectorAll('.view').forEach(view => {
        view.classList.remove('active');
    });

    // 显示选中的视图
    document.getElementById(`${viewName}-view`).classList.add('active');

    // 更新导航按钮状态
    document.querySelectorAll('.nav-btn').forEach(btn => {
        btn.classList.remove('active');
        if (btn.dataset.view === viewName) {
            btn.classList.add('active');
        }
    });

    // 加载对应的数据
    if (viewName === 'home') {
        loadHomeStats();
    } else if (viewName === 'skills') {
        loadSkills();
    } else if (viewName === 'categories') {
        loadCategories();
    }
}

// 导航按钮事件
document.querySelectorAll('.nav-btn').forEach(btn => {
    btn.addEventListener('click', () => {
        switchView(btn.dataset.view);
    });
});

// 首页统计
async function loadHomeStats() {
    try {
        const skillsResponse = await apiRequest('/skills');
        const categoriesResponse = await apiRequest('/categories');

        const totalSkills = skillsResponse.data.length;
        const publishedSkills = skillsResponse.data.filter(s => s.status === 'PUBLISHED').length;
        const totalCategories = categoriesResponse.data.length;

        document.getElementById('total-skills').textContent = totalSkills;
        document.getElementById('published-skills').textContent = publishedSkills;
        document.getElementById('total-categories').textContent = totalCategories;
    } catch (error) {
        console.error('加载统计数据失败:', error);
        showNotification('加载统计数据失败', 'error');
    }
}

// 技能管理
async function loadSkills() {
    // 填充分类筛选下拉框
    try {
        const catResp = await apiRequest('/categories');
        const filter = document.getElementById('skill-category-filter');
        filter.innerHTML = '<option value="">全部分类</option>';
        catResp.data.forEach(c => {
            filter.innerHTML += `<option value="${c.id}">${c.name}</option>`;
        });
    } catch (e) {}

    const skillsList = document.getElementById('skills-list');
    skillsList.innerHTML = '<div class="loading"><div class="spinner"></div><p>加载中...</p></div>';

    try {
        const response = await apiRequest('/skills');
        const skills = response.data;

        if (skills.length === 0) {
            skillsList.innerHTML = `
                <div class="empty-state">
                    <h3>暂无技能</h3>
                    <p>点击上方"创建技能"按钮开始添加技能</p>
                </div>
            `;
            return;
        }

        skillsList.innerHTML = skills.map(skill => `
            <div class="skill-card">
                ${getStatusBadge(skill.status)}
                <h3>${skill.name}</h3>
                <p>${skill.description || '暂无描述'}</p>
                <div class="skill-info">
                    <p><strong>开发者:</strong> ${skill.developer || '未知'}</p>
                    <p><strong>下载次数:</strong> ${skill.downloadCount || 0}</p>
                    <p><strong>使用次数:</strong> ${skill.useCount || 0}</p>
                    <p><strong>当前版本:</strong> ${getLatestVersion(skill)}</p>
                    <p><strong>版本数量:</strong> ${skill.versions.length}</p>
                    <p><strong>创建时间:</strong> ${formatDate(skill.createdAt)}</p>
                </div>
                <div class="skill-actions">
                    <button class="btn btn-primary" onclick="viewSkill(${skill.id})">查看</button>
                    <button class="btn btn-secondary" onclick="editSkill(${skill.id})">编辑</button>
                    ${skill.canPublish ? `<button class="btn btn-success" onclick="publishSkill(${skill.id})">发布</button>` : ''}
                    <button class="btn btn-warning" onclick="exportSkill(${skill.id})">导出</button>
                    <button class="btn btn-danger" onclick="deleteSkill(${skill.id})">删除</button>
                </div>
            </div>
        `).join('');
    } catch (error) {
        skillsList.innerHTML = `
            <div class="empty-state">
                <h3>加载失败</h3>
                <p>${error.message}</p>
            </div>
        `;
    }
}

// 创建技能表单
function showCreateSkillForm() {
    const formHtml = `
        <h2>创建技能</h2>
        <div id="skill-form-alert"></div>
        <form id="create-skill-form">
            <div class="form-group">
                <label>技能名称*</label>
                <input type="text" name="name" required>
            </div>
            <div class="form-group">
                <label>描述*</label>
                <textarea name="description" required></textarea>
            </div>
            <div class="form-group">
                <label>开发者*</label>
                <input type="text" name="developer" required>
            </div>
            <div class="form-group">
                <label>分类 *</label>
                <select name="categoryId" id="skill-category-select" required>
                    <option value="">请选择分类</option>
                </select>
            </div>
            <div class="form-group">
                <label>图标</label>
                <input type="file" name="iconFile" accept="image/*">
            </div>
            <div class="form-group">
                <label>技能包</label>
                <input type="file" name="packageFile">
            </div>
            <button type="submit" class="btn btn-primary">创建</button>
            <button type="button" class="btn btn-secondary" onclick="hideModal()">取消</button>
        </form>
    `;

    showModal(formHtml);
    loadCategoriesForSelect();
}

async function loadCategoriesForSelect(selectId) {
    try {
        const response = await apiRequest('/categories');
        const targetId = selectId || 'skill-category-select';
        const select = document.getElementById(targetId);
        if (!select) return;

        response.data.forEach(category => {
            const option = document.createElement('option');
            option.value = category.id;
            option.textContent = category.name;
            select.appendChild(option);
        });
    } catch (error) {
        console.error('加载分类失败:', error);
    }
}

document.addEventListener('submit', async (e) => {
    if (e.target.id === 'create-skill-form') {
        e.preventDefault();

        const form = e.target;
        const formData = new FormData(form);
        const alertDiv = document.getElementById('skill-form-alert');

        try {
            const response = await apiRequest('/skills', {
                method: 'POST',
                body: formData
            });

            if (response.success) {
                showNotification('技能创建成功');
                hideModal();
                loadSkills();
            } else {
                alertDiv.innerHTML = `<div class="alert alert-error">${response.message}</div>`;
            }
        } catch (error) {
            alertDiv.innerHTML = `<div class="alert alert-error">${error.message}</div>`;
        }
    }
});

// 查看技能详情
async function viewSkill(skillId) {
    try {
        const response = await apiRequest(`/skills/${skillId}`);
        const skill = response.data;

        const detailHtml = `
            <h2>技能详情</h2>
            <p><strong>名称:</strong> ${skill.name}</p>
            <p><strong>描述:</strong> ${skill.description || '暂无描述'}</p>
            <p><strong>状态:</strong> ${getStatusBadge(skill.status)}</p>
            <p><strong>开发者:</strong> ${skill.developer || '未知'}</p>
            <p><strong>下载次数:</strong> ${skill.downloadCount || 0}</p>
            <p><strong>使用次数:</strong> ${skill.useCount || 0}</p>
            <p><strong>创建时间:</strong> ${formatDate(skill.createdAt)}</p>
            <p><strong>更新时间:</strong> ${formatDate(skill.updatedAt)}</p>

            <h3>版本列表 (${skill.versions.length})</h3>
            ${skill.versions.length > 0 ? `
                <div class="versions-list">
                    ${skill.versions.slice().reverse().map(v => `
                        <div class="version-card ${v.isLatest ? 'latest' : ''} ${v.isRollback ? 'rollback' : ''}">
                            <div class="version-header">
                                <strong>v${v.version}</strong>
                                ${v.isLatest ? '<span class="badge badge-latest">最新</span>' : ''}
                                ${v.status === 'PUBLISHED' ? '<span class="badge badge-published">已发布</span>' : '<span class="badge badge-draft">草稿</span>'}
                                ${v.isRollback ? '<span class="badge badge-rollback">回滚版本</span>' : ''}
                            </div>
                            <div class="version-info">
                                <p><strong>创建时间:</strong> ${formatDate(v.createdAt)}</p>
                                ${v.changelog ? `<p><strong>更新日志:</strong> ${v.changelog}</p>` : ''}
                                ${v.isRollback && v.rolledBackFrom ? `<p><strong>回滚自:</strong> v${v.rolledBackFrom}</p>` : ''}
                            </div>
                            <div class="version-actions">
                                ${v.status === 'PUBLISHED' && !v.isLatest ? `
                                    <button class="btn btn-warning btn-sm" onclick="rollbackVersion(${skill.id}, ${v.id})">回滚到此版本</button>
                                ` : ''}
                                ${!v.isLatest ? `
                                    <button class="btn btn-danger btn-sm" onclick="deleteVersion(${skill.id}, ${v.id})">删除</button>
                                ` : ''}
                            </div>
                        </div>
                    `).join('')}
                </div>
            ` : '<p>暂无版本</p>'}

            <button class="btn btn-secondary" onclick="hideModal()">关闭</button>
        `;

        showModal(detailHtml);
    } catch (error) {
        showNotification('获取技能详情失败', 'error');
    }
}

// 编辑技能
async function editSkill(skillId) {
    try {
        const response = await apiRequest(`/skills/${skillId}`);
        const skill = response.data;

        const formHtml = `
            <h2>编辑技能</h2>
            <div id="edit-skill-alert"></div>
            <form id="edit-skill-form">
                <div class="form-group">
                    <label>技能名称*</label>
                    <input type="text" name="name" value="${skill.name}" required>
                </div>
                <div class="form-group">
                    <label>描述*</label>
                    <textarea name="description" required>${skill.description || ''}</textarea>
                </div>
                <div class="form-group">
                    <label>分类</label>
                    <select name="categoryId" id="edit-skill-category-select">
                        <option value="">请选择分类</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>图标</label>
                    <input type="file" name="iconFile" accept="image/*">
                    ${skill.iconUrl ? `<p>当前图标: ${skill.iconUrl}</p>` : ''}
                </div>
                <div class="form-group">
                    <label>技能包</label>
                    <input type="file" name="packageFile">
                    ${skill.packageUrl ? `<p>当前文件: ${skill.packageUrl}</p>` : ''}
                </div>
                <button type="submit" class="btn btn-primary">更新</button>
                <button type="button" class="btn btn-secondary" onclick="hideModal()">取消</button>
            </form>
        `;

        showModal(formHtml);
        window.currentEditingSkillId = skillId;

        // 设置当前分类
        setTimeout(async () => {
            await loadCategoriesForSelect('edit-skill-category-select');
            if (skill.categoryId) {
                document.getElementById('edit-skill-category-select').value = skill.categoryId;
            }
        }, 100);

    } catch (error) {
        showNotification('获取技能信息失败', 'error');
    }
}

// 编辑技能表单提交
document.addEventListener('submit', async (e) => {
    if (e.target.id === 'edit-skill-form') {
        e.preventDefault();

        const form = e.target;
        const name = form.querySelector('[name="name"]').value;
        const description = form.querySelector('[name="description"]').value;
        const categoryId = form.querySelector('[name="categoryId"]').value;
        const iconFile = form.querySelector('[name="iconFile"]').files[0];
        const packageFile = form.querySelector('[name="packageFile"]').files[0];
        const skillId = window.currentEditingSkillId;
        const alertDiv = document.getElementById('edit-skill-alert');

        try {
            const hasFile = (iconFile && iconFile.size > 0) || (packageFile && packageFile.size > 0);
            let response;

            if (hasFile) {
                const fd = new FormData();
                if (name) fd.append('name', name);
                if (description) fd.append('description', description);
                if (categoryId) fd.append('categoryId', categoryId);
                if (iconFile && iconFile.size > 0) fd.append('iconFile', iconFile);
                if (packageFile && packageFile.size > 0) fd.append('packageFile', packageFile);
                response = await fetch(`${API_BASE_URL}/skills/${skillId}`, { method: 'PUT', body: fd });
            } else {
                response = await fetch(`${API_BASE_URL}/skills/${skillId}`, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ name, description, categoryId })
                });
            }

            if (!response.ok) {
                const text = await response.text();
                throw new Error(text || '请求失败');
            }

            const data = await response.json();

            if (data.success) {
                showNotification('技能更新成功');
                hideModal();
                loadSkills();
            } else {
                alertDiv.innerHTML = `<div class="alert alert-error">${data.message}</div>`;
            }
        } catch (error) {
            alertDiv.innerHTML = `<div class="alert alert-error">${error.message}</div>`;
        }
    }
});

// 发布技能
async function publishSkill(skillId) {
    const formHtml = `
        <h2>发布技能 - 创建新版本</h2>
        <div id="publish-alert"></div>
        <form id="publish-skill-form">
            <div class="form-group">
                <label>更新日志</label>
                <textarea name="changelog" placeholder="描述本次更新的内容..."></textarea>
            </div>
            <button type="submit" class="btn btn-primary">发布</button>
            <button type="button" class="btn btn-secondary" onclick="hideModal()">取消</button>
        </form>
    `;

    showModal(formHtml);

    // 绑定表单提交事件
    document.getElementById('publish-skill-form').addEventListener('submit', async (e) => {
        e.preventDefault();

        const form = e.target;
        const formData = new FormData(form);
        const alertDiv = document.getElementById('publish-alert');

        try {
            const response = await apiRequest(`/skills/${skillId}/publish`, {
                method: 'POST',
                body: formData
            });

            if (response.success) {
                showNotification(response.message);
                hideModal();
                loadSkills();
            } else {
                alertDiv.innerHTML = `<div class="alert alert-error">${response.message}</div>`;
            }
        } catch (error) {
            alertDiv.innerHTML = `<div class="alert alert-error">${error.message}</div>`;
        }
    });
}

// 版本回滚
async function rollbackVersion(skillId, versionId) {
    if (!confirm('确定要回滚到此版本吗？这将创建一个新的版本。')) {
        return;
    }

    try {
        const response = await apiRequest(`/skills/${skillId}/versions/${versionId}/rollback`, {
            method: 'POST'
        });

        if (response.success) {
            showNotification(response.message);
            hideModal();
            viewSkill(skillId); // 重新加载详情
            loadSkills();
        }
    } catch (error) {
        showNotification('版本回滚失败: ' + error.message, 'error');
    }
}

// 删除版本
async function deleteVersion(skillId, versionId) {
    if (!confirm('确定要删除这个版本吗？此操作不可恢复。')) {
        return;
    }

    try {
        const response = await apiRequest(`/skills/${skillId}/versions/${versionId}`, {
            method: 'DELETE'
        });

        if (response.success) {
            showNotification('版本删除成功');
            hideModal();
            viewSkill(skillId); // 重新加载详情
            loadSkills();
        }
    } catch (error) {
        showNotification('版本删除失败: ' + error.message, 'error');
    }
}

// 删除技能
async function deleteSkill(skillId) {
    if (!confirm('确定要删除这个技能吗？此操作不可恢复。')) {
        return;
    }

    try {
        const response = await apiRequest(`/skills/${skillId}`, {
            method: 'DELETE'
        });

        if (response.success) {
            showNotification('技能删除成功');
            loadSkills();
        }
    } catch (error) {
        showNotification('技能删除失败', 'error');
    }
}

// 搜索技能
document.getElementById('search-skill-btn').addEventListener('click', async () => {
    const name = document.getElementById('skill-search').value;
    const status = document.getElementById('skill-status-filter').value;
    const categoryId = document.getElementById('skill-category-filter').value;

    let endpoint = '/skills';
    const params = [];
    if (name) params.push(`name=${encodeURIComponent(name)}`);
    if (status) params.push(`status=${status}`);
    if (categoryId) params.push(`categoryId=${categoryId}`);

    if (params.length > 0) {
        endpoint += '?' + params.join('&');
    }

    try {
        const response = await apiRequest(endpoint);
        const skills = response.data;
        const skillsList = document.getElementById('skills-list');

        if (skills.length === 0) {
            skillsList.innerHTML = `
                <div class="empty-state">
                    <h3>没有找到匹配的技能</h3>
                    <p>尝试其他搜索条件</p>
                </div>
            `;
            return;
        }

        skillsList.innerHTML = skills.map(skill => `
            <div class="skill-card">
                ${getStatusBadge(skill.status)}
                <h3>${skill.name}</h3>
                <p>${skill.description || '暂无描述'}</p>
                <div class="skill-info">
                    <p><strong>开发者:</strong> ${skill.developer || '未知'}</p>
                    <p><strong>下载次数:</strong> ${skill.downloadCount || 0}</p>
                    <p><strong>使用次数:</strong> ${skill.useCount || 0}</p>
                </div>
                <div class="skill-actions">
                    <button class="btn btn-primary" onclick="viewSkill(${skill.id})">查看</button>
                    <button class="btn btn-secondary" onclick="editSkill(${skill.id})">编辑</button>
                    ${skill.canPublish ? `<button class="btn btn-success" onclick="publishSkill(${skill.id})">发布</button>` : ''}
                    <button class="btn btn-warning" onclick="exportSkill(${skill.id})">导出</button>
                    <button class="btn btn-danger" onclick="deleteSkill(${skill.id})">删除</button>
                </div>
            </div>
        `).join('');
    } catch (error) {
        showNotification('搜索失败', 'error');
    }
});

// 分类管理
async function loadCategories() {
    const categoriesList = document.getElementById('categories-list');
    categoriesList.innerHTML = '<div class="loading"><div class="spinner"></div><p>加载中...</p></div>';

    try {
        const response = await apiRequest('/categories');
        const categories = response.data;

        if (categories.length === 0) {
            categoriesList.innerHTML = `
                <div class="empty-state">
                    <h3>暂无分类</h3>
                    <p>点击上方"创建分类"按钮开始添加分类</p>
                </div>
            `;
            return;
        }

        categoriesList.innerHTML = categories.map(category => `
            <div class="category-card">
                <h3>${category.name}</h3>
                <p>${category.description || '暂无描述'}</p>
                <p><strong>排序:</strong> ${category.sortOrder || 0}</p>
                <p><strong>创建时间:</strong> ${formatDate(category.createdAt)}</p>
                <div class="category-actions">
                    <button class="btn btn-secondary" onclick="editCategory(${category.id})">编辑</button>
                    <button class="btn btn-danger" onclick="deleteCategory(${category.id})">删除</button>
                </div>
            </div>
        `).join('');
    } catch (error) {
        categoriesList.innerHTML = `
            <div class="empty-state">
                <h3>加载失败</h3>
                <p>${error.message}</p>
            </div>
        `;
    }
}

// 创建分类表单
function showCreateCategoryForm() {
    const formHtml = `
        <h2>创建分类</h2>
        <div id="category-form-alert"></div>
        <form id="create-category-form">
            <div class="form-group">
                <label>分类名称*</label>
                <input type="text" name="name" required>
            </div>
            <div class="form-group">
                <label>描述</label>
                <textarea name="description"></textarea>
            </div>
            <div class="form-group">
                <label>排序</label>
                <input type="number" name="sortOrder" value="0">
            </div>
            <button type="submit" class="btn btn-primary">创建</button>
            <button type="button" class="btn btn-secondary" onclick="hideModal()">取消</button>
        </form>
    `;

    showModal(formHtml);
}

document.addEventListener('submit', async (e) => {
    if (e.target.id === 'create-category-form') {
        e.preventDefault();

        const form = e.target;
        const formData = new FormData(form);
        const alertDiv = document.getElementById('category-form-alert');

        try {
            const response = await apiRequest('/categories', {
                method: 'POST',
                body: formData
            });

            if (response.success) {
                showNotification('分类创建成功');
                hideModal();
                loadCategories();
            } else {
                alertDiv.innerHTML = `<div class="alert alert-error">${response.message}</div>`;
            }
        } catch (error) {
            alertDiv.innerHTML = `<div class="alert alert-error">${error.message}</div>`;
        }
    }
});

// 编辑分类
async function editCategory(categoryId) {
    try {
        const response = await apiRequest(`/categories/${categoryId}`);
        const category = response.data;

        const formHtml = `
            <h2>编辑分类</h2>
            <div id="edit-category-alert"></div>
            <form id="edit-category-form">
                <div class="form-group">
                    <label>分类名称*</label>
                    <input type="text" name="name" value="${category.name}" required>
                </div>
                <div class="form-group">
                    <label>描述</label>
                    <textarea name="description">${category.description || ''}</textarea>
                </div>
                <div class="form-group">
                    <label>排序</label>
                    <input type="number" name="sortOrder" value="${category.sortOrder || 0}">
                </div>
                <button type="submit" class="btn btn-primary">更新</button>
                <button type="button" class="btn btn-secondary" onclick="hideModal()">取消</button>
            </form>
        `;

        showModal(formHtml);
        window.currentEditingCategoryId = categoryId;

    } catch (error) {
        showNotification('获取分类信息失败', 'error');
    }
}

document.addEventListener('submit', async (e) => {
    if (e.target.id === 'edit-category-form') {
        e.preventDefault();

        const form = e.target;
        const formData = new FormData(form);
        const alertDiv = document.getElementById('edit-category-alert');
        const categoryId = window.currentEditingCategoryId;

        try {
            const response = await apiRequest(`/categories/${categoryId}`, {
                method: 'PUT',
                body: formData
            });

            if (response.success) {
                showNotification('分类更新成功');
                hideModal();
                loadCategories();
            } else {
                alertDiv.innerHTML = `<div class="alert alert-error">${response.message}</div>`;
            }
        } catch (error) {
            alertDiv.innerHTML = `<div class="alert alert-error">${error.message}</div>`;
        }
    }
});

// 删除分类
async function deleteCategory(categoryId) {
    if (!confirm('确定要删除这个分类吗？')) {
        return;
    }

    try {
        const response = await apiRequest(`/categories/${categoryId}`, {
            method: 'DELETE'
        });

        if (response.success) {
            showNotification('分类删除成功');
            loadCategories();
        }
    } catch (error) {
        showNotification('分类删除失败', 'error');
    }
}

// 按钮事件绑定
document.getElementById('create-skill-btn').addEventListener('click', showCreateSkillForm);
document.getElementById('create-category-btn').addEventListener('click', showCreateCategoryForm);

// 页面加载时初始化
document.addEventListener('DOMContentLoaded', () => {
    loadHomeStats();
});