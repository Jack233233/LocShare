// 前端逻辑
let currentMode = 'none';
let isSharing = false;
let selectedFriend = null;
let friends = [];

// 页面切换
function enterPairMode() {
    console.log('enterPairMode called');
    currentMode = 'pair';

    // 隐藏主菜单，显示好友模式
    document.getElementById('main-menu').classList.remove('active');
    document.getElementById('pair-mode').classList.add('active');

    // 显示地图容器
    const mapContainer = document.getElementById('map-container');
    mapContainer.classList.remove('hidden');
    mapContainer.classList.add('visible');

    console.log('pair-mode active:', document.getElementById('pair-mode').classList.contains('active'));

    // 加载保存的用户名
    const savedName = localStorage.getItem('userName') || '';
    document.getElementById('userName').value = savedName;

    // 加载好友列表
    loadFriends();

    // 通知原生显示地图
    if (window.AndroidBridge) {
        AndroidBridge.showMap();
    }
}

function enterMultiMode() {
    currentMode = 'multi';
    document.getElementById('main-menu').classList.remove('active');
    document.getElementById('multi-mode').classList.add('active');

    // 显示地图容器
    const mapContainer = document.getElementById('map-container');
    mapContainer.classList.remove('hidden');
    mapContainer.classList.add('visible');

    const savedName = localStorage.getItem('userName') || '';
    document.getElementById('multiUserName').value = savedName;

    if (window.AndroidBridge) {
        AndroidBridge.showMap();
    }
}

function backToMenu() {
    currentMode = 'none';
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.getElementById('main-menu').classList.add('active');

    // 隐藏地图容器
    const mapContainer = document.getElementById('map-container');
    mapContainer.classList.add('hidden');
    mapContainer.classList.remove('visible');

    if (window.AndroidBridge) {
        AndroidBridge.hideMap();
    }
}

// 生成配对码
function generatePairCode() {
    const userName = document.getElementById('userName').value.trim();
    if (!userName) {
        showToast('请先输入你的名字');
        return;
    }

    localStorage.setItem('userName', userName);

    if (window.AndroidBridge) {
        AndroidBridge.generatePairCode(userName);
    }
}

// 显示配对码
function showPairCode(code) {
    const display = document.getElementById('pair-code-display');
    display.querySelector('.code').textContent = code;
    display.classList.remove('hidden');

    // 5分钟后隐藏
    setTimeout(() => {
        display.classList.add('hidden');
    }, 300000);
}

// 输入配对码
function showInputCode() {
    const userName = document.getElementById('userName').value.trim();
    if (!userName) {
        showToast('请先输入你的名字');
        return;
    }

    localStorage.setItem('userName', userName);
    document.getElementById('input-code-modal').classList.remove('hidden');
}

function hideModal() {
    document.getElementById('input-code-modal').classList.add('hidden');
    document.getElementById('input-code').value = '';
}

function submitPairCode() {
    const code = document.getElementById('input-code').value.trim();
    if (code.length !== 6) {
        showToast('请输入6位配对码');
        return;
    }

    const userName = document.getElementById('userName').value.trim();

    if (window.AndroidBridge) {
        AndroidBridge.pairWithCode(code, userName);
    }

    hideModal();
}

// 好友列表
function toggleFriends() {
    const list = document.getElementById('friends-list');
    const icon = document.getElementById('expand-icon');

    list.classList.toggle('collapsed');
    icon.textContent = list.classList.contains('collapsed') ? '▶' : '▼';
}

function loadFriends() {
    if (window.AndroidBridge) {
        AndroidBridge.loadFriends();
    }
}

function updateFriendsList(friendsData) {
    friends = friendsData;
    const container = document.getElementById('friends-list');
    const countEl = document.getElementById('friend-count');

    countEl.textContent = `(${friends.length})`;

    if (friends.length === 0) {
        container.innerHTML = '<div style="padding: 20px; text-align: center; color: #666;">暂无好友，生成配对码添加</div>';
        return;
    }

    container.innerHTML = friends.map(f => `
        <div class="friend-item ${selectedFriend === f.friendId ? 'selected' : ''}"
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

    updateShareButton();
}

function selectFriend(friendId) {
    selectedFriend = friendId;

    // 更新UI
    document.querySelectorAll('.friend-item').forEach(el => {
        el.classList.toggle('selected', el.dataset.id === friendId);
    });

    updateShareButton();
}

function deleteFriend(friendId) {
    if (confirm('确定要删除这个好友吗？')) {
        if (window.AndroidBridge) {
            AndroidBridge.deleteFriend(friendId);
        }
    }
}

function updateShareButton() {
    const btn = document.getElementById('btn-share');
    if (currentMode === 'pair') {
        btn.disabled = !selectedFriend;
    } else if (currentMode === 'multi') {
        const roomId = document.getElementById('roomId').value.trim();
        btn.disabled = !roomId;
    }
}

// 共享控制
function toggleSharing() {
    if (isSharing) {
        stopSharing();
    } else {
        startSharing();
    }
}

function startSharing() {
    const userName = document.getElementById('userName').value.trim();
    if (!userName) {
        showToast('请先输入你的名字');
        return;
    }

    localStorage.setItem('userName', userName);

    if (currentMode === 'pair' && selectedFriend) {
        const friend = friends.find(f => f.friendId === selectedFriend);
        if (friend && window.AndroidBridge) {
            AndroidBridge.startPairSharing(userName, friend.pairRoomId);
        }
    }
}

function startMultiSharing() {
    const userName = document.getElementById('multiUserName').value.trim();
    const roomId = document.getElementById('roomId').value.trim();

    if (!userName) {
        showToast('请先输入你的名字');
        return;
    }
    if (!roomId) {
        showToast('请输入房间号');
        return;
    }

    localStorage.setItem('userName', userName);

    if (window.AndroidBridge) {
        AndroidBridge.startMultiSharing(userName, roomId);
    }
}

function stopSharing() {
    if (window.AndroidBridge) {
        AndroidBridge.stopSharing();
    }
}

function onSharingStarted() {
    isSharing = true;

    if (currentMode === 'pair') {
        document.getElementById('btn-share').classList.add('hidden');
        document.getElementById('btn-stop').classList.remove('hidden');
    } else {
        document.getElementById('btn-multi-share').classList.add('hidden');
        document.getElementById('btn-multi-stop').classList.remove('hidden');
    }
}

function onSharingStopped() {
    isSharing = false;

    document.getElementById('btn-share').classList.remove('hidden');
    document.getElementById('btn-stop').classList.add('hidden');
    document.getElementById('btn-multi-share').classList.remove('hidden');
    document.getElementById('btn-multi-stop').classList.add('hidden');
}

// 地图跟随
function toggleFollow() {
    if (window.AndroidBridge) {
        AndroidBridge.toggleFollow();
    }
}

// Toast提示
function showToast(message) {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.classList.remove('hidden');

    setTimeout(() => {
        toast.classList.add('hidden');
    }, 3000);
}

// 监听输入变化
document.getElementById('roomId')?.addEventListener('input', updateShareButton);

// 原生回调接口
window.onPairCodeGenerated = function(code) {
    showPairCode(code);
    showToast('配对码已生成');
};

window.onFriendsLoaded = function(friendsJson) {
    const friendsData = JSON.parse(friendsJson);
    updateFriendsList(friendsData);
};

window.onFriendAdded = function(success, message) {
    showToast(message);
    if (success) {
        loadFriends();
    }
};

window.onSharingStateChanged = function(sharing) {
    if (sharing) {
        onSharingStarted();
    } else {
        onSharingStopped();
    }
};

window.onLocationUpdate = function(userId, lat, lng, name) {
    // 可以在这里更新前端显示的位置信息
    console.log('Location update:', userId, lat, lng, name);
};

// 初始化
window.onload = function() {
    // 检查是否有保存的用户名
    const savedName = localStorage.getItem('userName');
    if (savedName) {
        document.getElementById('userName').value = savedName;
        document.getElementById('multiUserName').value = savedName;
    }
};
