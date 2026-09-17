// Bubble Anyway server-event example for Forge 1.19.2.
// This sends directly through the mod API and does not execute /bubble.
const BubbleServer = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSServerApi');

BubbleServer.onClick(event => {
  const controlId = String(event.control.id);
  const bubbleId = String(event.bubble.id);

  if (controlId === 'confirm') {
    event.player.tell('你点击了确定按钮');
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
    text: '是否领取示例奖励？',
    controls: {
      layout: 'HORIZONTAL',
      gap: 6,
      align: 'CENTER',
      items: [
        {id: 'confirm', text: '领取', data: {reward: 'minecraft:diamond', count: 1}},
        {id: 'cancel', text: '以后再说'}
      ]
    }
  }));
});

// The same API can be called from quest, kill, block, or custom server events.
