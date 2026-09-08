// Bubble Anyway server-event example for Forge 1.20.1.
// This sends directly through the mod API and does not execute /bubble.
const BubbleServer = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSServerApi');

ServerEvents.loaded(event => {
  BubbleServer.showAllJson(event.server, JSON.stringify({
    id: 'server_loaded',
    text: 'Server scripts are ready\nWelcome!',
    anchor: 'CENTER_TOP',
    y: 18,
    animation: 'SLIDE_FROM_TOP',
    duration: 100,
    priority: 100,
    replace: true
  }));
});

PlayerEvents.loggedIn(event => {
  BubbleServer.showJson(event.player, JSON.stringify({
    id: 'login_hint',
    text: '欢迎进入服务器',
    icon: 'minecraft:diamond',
    anchor: 'CENTER_TOP',
    y: 18,
    animation: 'SLIDE_FROM_TOP',
    duration: 100,
    priority: 100
  }));
});

// The client resolves the theme locally. If it is missing, it requests this
// one theme from the server and caches it for the current connection.
PlayerEvents.loggedIn(event => {
  BubbleServer.showJson(event.player, JSON.stringify({
    id: 'login_theme_hint',
    theme: 'bubble_anyway:warning',
    text: '主题气泡已启用'
  }));
});

// Override only the fields that differ from the preset.
ServerEvents.loaded(event => {
  BubbleServer.showAllThemeJson(event.server, 'my_mod:quest_notice', JSON.stringify({
    text: '数据包主题示例',
    priority: 180
  }));
});

// The same API can be called from quest, kill, block, or custom server events.
