// Put this file in kubejs/client_scripts for Forge 1.20.1.
// This runs locally on each client and does not send a server command.
const BubbleAnyway = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSBindings');

ClientEvents.loggedIn(event => {
  // With a theme, all omitted style fields come from the local theme.
  BubbleAnyway.showJson(JSON.stringify({
    id: 'client_welcome',
    theme: 'bubble_anyway:warning',
    text: 'Client KubeJS is ready\nThis bubble is local only.',
    priority: 120
  }));
});

// Short form:
// BubbleAnyway.show('A local client-side message');
// BubbleAnyway.clear();
