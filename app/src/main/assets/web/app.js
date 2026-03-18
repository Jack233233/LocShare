// ========== 状态管理 ==========
const state = {
    currentMode: 'none', // 'none', 'pair', 'multi'
    isSharing: false,
    isSheetExpanded: false,
    selectedFriend: null,
    friends: [],
    userInfo: null
};

// ========== 初始化 ==========
document.addEventListener('DOMContentLoaded', () => {
    initUserInfo();
    initEventListeners();
});

function initUserInfo() {
    if (window.AndroidBridge) {
        const info = JSON.parse(AndroidBridge.getUserInfo());
        state.userInfo = info;
    }
}

function initEventListeners() {
    // 房间号输入监听
    const roomInput = document.getElementById('room-id-input');
    if (roomInput) {
        roomInput.addEventListener('input', updateShareButtonState);
    }

    // 配对码输入限制
    const codeInput = document.getElementById('code-input');
    if (codeInput) {
        codeInput.addEventListener('input', (e) => {
            if (e.target.value.length > 6) {
                e.target.value = e.target.value.slice(0, 6);
            }
        });
    }
}

// ========== 底部抽屉控制（由原生 BottomSheetBehavior 驱动）==========

// 原生回调：抽屉状态变化
window.onSheetStateChanged = function(stateStr) {
    state.isSheetExpanded = (stateStr === 'expanded');
    updateSheetUI();
};

// 原生回调：抽屉滑动中（用于视差效果）
window.onSheetSliding = function(offset) {
    // offset: 0 (折叠) ~ 1 (展开)
    // 可以在这里添加视差动画效果
    const hint = document.getElementById('sheet-hint');
    if (hint) {
        if (offset > 0.5) {
            hint.textContent = '👇 下滑关闭';
        } else {
            hint.textContent = '👆 上滑展开';
        }
    }
};

function updateSheetUI() {
    const sheet = document.getElementById('bottom-sheet');
    const hint = document.getElementById('sheet-hint');

    if (state.isSheetExpanded) {
        sheet.classList.add('expanded');
        sheet.classList.remove('collapsed');
        if (hint) hint.textContent = '👇 下滑关闭';
    } else {
        sheet.classList.remove('expanded');
        sheet.classList.add('collapsed');
        if (hint) hint.textContent = '👆 上滑展开';
    }
}

// 点击手柄切换状态
function toggleSheet() {
    if (window.AndroidBridge) {
        if (state.isSheetExpanded) {
            AndroidBridge.collapseSheet();
        } else {
            AndroidBridge.expandSheet();
        }
    }
}

// ========== 模式切换 ==========
function showPairMode() {
    state.currentMode = 'pair';
    document.getElementById('pair-panel').classList.remove('hidden');
    document.getElementById('multi-panel').classList.add('hidden');
    loadFriends();
    updateShareButtonState();
}

function showMultiMode() {
    state.currentMode = 'multi';
    document.getElementById('pair-panel').classList.add('hidden');
    document.getElementById('multi-panel').classList.remove('hidden');
    updateShareButtonState();
}

function showRoutes() {
    if (window.AndroidBridge) {
        AndroidBridge.showRouteManager();
    }
}

function showProfile() {
    if (window.AndroidBridge) {
        AndroidBridge.showProfile();
    }
}

// ========== 配对码 ==========
function generatePairCode() {
    const userName = state.userInfo?.userName || '';
    if (!userName) {
        showToast('请先设置昵称');
        return;
    }

    if (window.AndroidBridge) {
        AndroidBridge.generatePairCode(userName);
    }
}

function showInputCode() {
    document.getElementById('code-modal').classList.remove('hidden');
    setTimeout(() => {
        document.getElementById('code-input').focus();
    }, 100);
}

function hideCodeModal() {
    document.getElementById('code-modal').classList.add('hidden');
    document.getElementById('code-input').value = '';
}

function submitCode() {
    const code = document.getElementById('code-input').value.trim();
    if (code.length !== 6) {
        showToast('请输入6位配对码');
        return;
    }

    const userName = state.userInfo?.userName || '';
    if (window.AndroidBridge) {
        AndroidBridge.pairWithCode(code, userName);
    }
    hideCodeModal();
}

// ========== 好友列表 ==========
function toggleFriends() {
    const section = document.querySelector('.friends-section');
    section.classList.toggle('collapsed');
}

function loadFriends() {
    if (window.AndroidBridge) {
        AndroidBridge.loadFriends();
    }
}

function selectFriend(friendId) {
    state.selectedFriend = state.friends.find(f => f.friendId === friendId);

    // 更新UI选中状态
    document.querySelectorAll('.friend-item').forEach(el => {
        el.classList.toggle('selected', el.dataset.id === friendId);
    });

    updateShareButtonState();
}

function deleteFriend(friendId) {
    if (confirm('确定要删除这个好友吗？')) {
        if (window.AndroidBridge) {
            AndroidBridge.deleteFriend(friendId);
        }
    }
}

// ========== 共享控制 ==========
function toggleSharing() {
    if (state.isSharing) {
        stopSharing();
    } else {
        startSharing();
    }
}

function startSharing() {
    const userName = state.userInfo?.userName || '';
    if (!userName) {
        showToast('请先设置昵称');
        return;
    }

    if (state.currentMode === 'pair' && state.selectedFriend) {
        if (window.AndroidBridge) {
            AndroidBridge.startPairSharing(userName, state.selectedFriend.pairRoomId);
        }
    } else if (state.currentMode === 'multi') {
        const roomId = document.getElementById('room-id-input').value.trim();
        if (!roomId) {
            showToast('请输入房间号');
            return;
        }
        if (window.AndroidBridge) {
            AndroidBridge.startMultiSharing(userName, roomId);
        }
    }
}

function stopSharing() {
    if (window.AndroidBridge) {
        AndroidBridge.stopSharing();
    }
}

function updateShareButtonState() {
    const btn = document.getElementById('btn-share');

    if (state.isSharing) {
        btn.textContent = '停止共享';
        btn.classList.add('stopping');
        btn.disabled = false;
        return;
    }

    btn.textContent = '开始共享';
    btn.classList.remove('stopping');

    let canStart = false;
    if (state.currentMode === 'pair') {
        canStart = state.selectedFriend !== null;
    } else if (state.currentMode === 'multi') {
        const roomId = document.getElementById('room-id-input')?.value.trim();
        canStart = !!roomId;
    }

    btn.disabled = !canStart;
}

// ========== Toast 提示 ==========
function showToast(message) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.classList.add('show');

    setTimeout(() => {
        toast.classList.remove('show');
    }, 2500);
}

// ========== 原生回调接口 ==========

// 配对码生成回调
window.onPairCodeGenerated = function(code) {
    document.getElementById('pair-code').textContent = code;
    document.getElementById('pair-code-display').classList.remove('hidden');
    showToast('配对码已生成');

    // 5分钟后隐藏
    setTimeout(() => {
        document.getElementById('pair-code-display').classList.add('hidden');
    }, 300000);
};

// 好友列表加载回调
window.onFriendsLoaded = function(friendsJson) {
    try {
        state.friends = JSON.parse(friendsJson);
        renderFriendsList();
    } catch (e) {
        console.error('Failed to parse friends:', e);
    }
};

function renderFriendsList() {
    const container = document.getElementById('friends-list');
    const countEl = document.getElementById('friend-count');

    countEl.textContent = `(${state.friends.length})`;

    if (state.friends.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">💫</div>
                <div class="empty-text">暂无好友</div>
                <div class="empty-hint">生成配对码添加第一个重要的人</div>
            </div>
        `;
        return;
    }

    container.innerHTML = state.friends.map(f => `
        <div class="friend-item ${state.selectedFriend?.friendId === f.friendId ? 'selected' : ''}"
             onclick="selectFriend('${f.friendId}')"
             data-id="${f.friendId}">
            <div class="friend-avatar">${f.friendName.charAt(0)}</div>
            <div class="friend-info">
                <div class="friend-name">${f.friendName}</div>
                <div class="friend-status">点击开始共享</div>
            </div>
            <div class="friend-delete" onclick="event.stopPropagation(); deleteFriend('${f.friendId}')">删除</div>
        </div>
    `).join('');
}

// 添加好友回调
window.onFriendAdded = function(success, message) {
    showToast(message);
    if (success) {
        loadFriends();
    }
};

// 共享状态变化回调
window.onSharingStateChanged = function(sharing) {
    state.isSharing = sharing;
    updateShareButtonState();
};

// 用户加入回调
window.onUserJoined = function(userName) {
    showToast(`${userName} 加入了共享`);
};

// 用户离开回调
window.onUserLeft = function(userId) {
    console.log('User left:', userId);
};

// 新好友回调
window.onNewFriend = function(friendName) {
    showToast(`${friendName} 添加你为好友`);
    loadFriends();
};

// 位置更新回调
window.onLocationUpdate = function(userId, lat, lng, name) {
    console.log('Location update:', userId, lat, lng, name);
};

// 标记点击回调
window.onMarkerClicked = function(userId, userName) {
    console.log('Marker clicked:', userId, userName);
};
