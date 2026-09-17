// Bubble Anyway server-event example for Forge 1.20.1.
// This sends directly through the mod API and does not execute /bubble.
const BubbleServer = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSServerApi');

BubbleServer.onClick(event => {
  const controlId = String(event.control.id);
  const bubbleId = String(event.bubble.id);

  if (controlId === 'confirm') {
    event.player.tell('你点击了确定按钮');
    // event.bubble, event.control and event.data are available here.
    console.info('Bubble control clicked: ' + bubbleId + '/' + controlId);
  } else if (controlId === 'cancel') {
    event.player.tell('你点击了取消按钮');
  }
});

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

PlayerEvents.loggedIn(event => {
  BubbleServer.showJson(event.player, JSON.stringify({
    id: 'confirm_example',
    theme: 'bubble_anyway:warning',
    text: '是否领取示例奖励？',
    controlOverrides: {
      confirm: {text: '领取', data: {quest: 'my_mod:first_quest'}},
      cancel: {text: '以后再说'},
      details: {text: '查看详情', closeOnPress: false}
    }
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
