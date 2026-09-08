// Bubble Anyway server-event example for Fabric 1.20.1.
// Put this file in kubejs/server_scripts. It does not execute /bubble.
const BubbleServer = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSServerApi');

ServerEvents.loaded(event => {
  BubbleServer.showAllJson(event.server, JSON.stringify({
    id: 'server_loaded',
    text: 'Server scripts are ready\nWelcome!',
    color: '#FFFFFFFF',
    backgroundColor: '#D91B2430',
    anchor: 'CENTER_TOP',
    x: 0,
    y: 18,
    animation: 'SLIDE_FROM_TOP',
    duration: 100,
    fadeIn: 10,
    fadeOut: 14,
    priority: 100,
    fontSize: 1.0,
    bold: true,
    sound: 'minecraft:ui.button.click',
    replace: true
  }));
});

PlayerEvents.loggedIn(event => {
  BubbleServer.showJson(event.player, JSON.stringify({
    id: 'login_hint',
    text: 'Welcome to the server',
    icon: 'minecraft:diamond',
    anchor: 'CENTER_TOP',
    y: 18,
    animation: 'SLIDE_FROM_TOP',
    duration: 100,
    priority: 100
  }));
});

// The same API can be called from quest, kill, block, or custom server events.
