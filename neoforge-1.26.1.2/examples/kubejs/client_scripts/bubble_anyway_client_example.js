// Put this file in kubejs/client_scripts for NeoForge 26.1.2.
// This runs locally on each client and does not send a server command.
const BubbleAnyway = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSBindings');

BubbleAnyway.onClick(event => {
  console.info('Local bubble control clicked: '
    + event.bubble.id + '/' + event.control.id);
});

ClientEvents.loggedIn(event => {
  BubbleAnyway.showJson(JSON.stringify({
    id: 'client_welcome',
    text: 'Client KubeJS is ready\nThis bubble is local only.',
    color: '#FFFFFF',
    backgroundColor: '#D91B2430',
    anchor: 'CENTER_TOP',
    y: 18,
    animation: 'SLIDE_FROM_TOP',
    sound: 'minecraft:ui.button.click',
    soundVolume: 1.0,
    soundPitch: 1.0,
    duration: 100,
    fadeIn: 10,
    fadeOut: 14,
    priority: 100,
    bold: true
  }));
});

// Short form:
// BubbleAnyway.show('A local client-side message');
// BubbleAnyway.clear();
