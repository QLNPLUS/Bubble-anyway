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

// The same API can be called from quest, kill, block, or custom server events.
