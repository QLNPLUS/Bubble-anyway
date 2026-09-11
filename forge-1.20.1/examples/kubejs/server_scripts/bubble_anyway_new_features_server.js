// Bubble Anyway Forge 1.20.1 server-to-client feature test.
// Copy to kubejs/server_scripts. This verifies server network serialization.
const BubbleServer = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSServerApi');

PlayerEvents.loggedIn(event => {
  BubbleServer.showJson(event.player, JSON.stringify({
    id: 'server_rich_parts',
    textParts: [
      { text: '服务端发送的标题', color: '#FFFFFF', bold: true, scale: 1.1 },
      { text: '\n服务端发送的副标题', color: '#55FFFF', italic: true, scale: 0.9 }
    ],
    icon: 'minecraft:textures/item/diamond.png',
    iconType: 'TEXTURE',
    iconSize: 26,
    anchor: 'TOP_RIGHT',
    x: -12,
    y: 12,
    animation: 'SLIDE_FROM_RIGHT',
    fadeIn: 8,
    fadeOut: 12,
    slideIn: 10,
    slideOut: 14,
    duration: 100,
    background: 'bubble_anyway:textures/gui/background_modern.png',
    backgroundBorder: 8,
    backgroundGuide: 1,
    priority: 220,
    replace: true
  }));

  // Theme/network test:
  // BubbleServer.showJson(event.player, JSON.stringify({
  //   id: 'server_theme_test',
  //   theme: 'bubble_anyway:test_rich',
  //   text: '主题由客户端本地读取'
  // }));
});
