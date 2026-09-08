// Bubble Anyway capability showcase for Fabric 1.21.1.
// Copy this file to kubejs/client_scripts and join a world to start the demo.
// The demo is client-local and uses ClientEvents.tick as its delay timer.
const BubbleAnyway = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSBindings');

const SHOWCASE_INTERVAL_TICKS = 70;
const SHOWCASE = [
  {
    id: 'showcase_top_left',
    text: 'TOP_LEFT\nSlide in from the left',
    anchor: 'TOP_LEFT',
    x: 10,
    y: 10,
    animation: 'SLIDE_FROM_LEFT',
    duration: 62,
    fadeIn: 10,
    fadeOut: 12,
    color: '#FFFFFFFF',
    backgroundColor: '#E62E7D32',
    bold: true,
    shadow: true,
    replace: true
  },
  {
    id: 'showcase_center_top',
    text: 'CENTER_TOP\nCentered text and diamond icon',
    icon: 'minecraft:diamond',
    iconSize: 20,
    iconGap: 8,
    iconOffsetX: 2,
    iconOffsetY: -1,
    textOffsetX: 4,
    textOffsetY: 1,
    anchor: 'CENTER_TOP',
    y: 18,
    textAlign: 'CENTER',
    background: 'bubble_anyway:textures/gui/background.png',
    backgroundBorder: 8,
    backgroundGuide: 1,
    padding: 12,
    width: 250,
    animation: 'SLIDE_FROM_TOP',
    duration: 62,
    fadeIn: 8,
    fadeOut: 14,
    priority: 30,
    replace: true
  },
  {
    id: 'showcase_top_right',
    text: 'TOP_RIGHT\nRight aligned text',
    anchor: 'TOP_RIGHT',
    x: -10,
    y: 10,
    textAlign: 'RIGHT',
    width: 190,
    padding: 9,
    animation: 'SLIDE_FROM_RIGHT',
    duration: 62,
    fadeIn: 14,
    fadeOut: 8,
    color: '#FFFFE082',
    backgroundColor: '#E64A235A',
    italic: true,
    replace: true
  },
  {
    id: 'showcase_center_left',
    text: 'CENTER_LEFT\nAuto width\nNo icon or texture',
    anchor: 'CENTER_LEFT',
    x: 10,
    y: 0,
    animation: 'SLIDE_FROM_LEFT',
    duration: 62,
    fadeIn: 6,
    fadeOut: 16,
    color: '#FFE8F5E9',
    backgroundColor: '#E61B5E20',
    scale: 0.9,
    underlined: true,
    replace: true
  },
  {
    id: 'showcase_center',
    text: 'CENTER\nLarge text, fixed height, and strikethrough',
    anchor: 'CENTER',
    textAlign: 'CENTER',
    width: 280,
    height: 66,
    padding: 14,
    fontSize: 1.25,
    strikethrough: true,
    color: '#FFFFF59D',
    backgroundColor: '#E6513131',
    animation: 'FADE',
    duration: 62,
    fadeIn: 18,
    fadeOut: 18,
    priority: 50,
    replace: true
  },
  {
    id: 'showcase_center_right',
    text: 'CENTER_RIGHT\nSmall icon and long text wraps automatically',
    icon: 'minecraft:emerald',
    iconSize: 14,
    iconGap: 5,
    anchor: 'CENTER_RIGHT',
    x: -10,
    y: 0,
    maxWidth: 210,
    padding: 8,
    animation: 'SLIDE_FROM_RIGHT',
    duration: 62,
    fadeIn: 12,
    fadeOut: 10,
    color: '#FFE0F7FA',
    backgroundColor: '#E600607D',
    italic: true,
    replace: true
  },
  {
    id: 'showcase_bottom_left',
    text: 'BOTTOM_LEFT\nFade in and fade out',
    anchor: 'BOTTOM_LEFT',
    x: 10,
    y: 10,
    animation: 'FADE',
    duration: 62,
    fadeIn: 20,
    fadeOut: 20,
    color: '#FFFFCCBC',
    backgroundColor: '#E6BF360C',
    padding: 11,
    replace: true
  },
  {
    id: 'showcase_center_bottom',
    text: 'CENTER_BOTTOM\nBottom edge slide with item icon',
    icon: 'minecraft:nether_star',
    iconSize: 18,
    anchor: 'CENTER_BOTTOM',
    y: 16,
    animation: 'SLIDE_FROM_BOTTOM',
    duration: 62,
    fadeIn: 10,
    fadeOut: 12,
    background: 'bubble_anyway:textures/gui/background.png',
    backgroundBorder: 8,
    padding: 10,
    color: '#FF263238',
    textAlign: 'CENTER',
    bold: true,
    replace: true
  },
  {
    id: 'showcase_bottom_right',
    text: 'BOTTOM_RIGHT\nCompact auto layout',
    icon: 'minecraft:clock',
    iconSize: 16,
    iconGap: 4,
    anchor: 'BOTTOM_RIGHT',
    x: -10,
    y: 10,
    animation: 'SLIDE_FROM_RIGHT',
    duration: 62,
    fadeIn: 16,
    fadeOut: 6,
    color: '#FFFFFFFF',
    backgroundColor: '#E68A1C7C',
    scale: 1.1,
    shadow: false,
    replace: true
  }
];

let showcaseTick = 0;
let showcaseIndex = 0;
let showcaseStarted = false;

ClientEvents.loggedIn(event => {
  showcaseTick = 0;
  showcaseIndex = 0;
  showcaseStarted = true;
});

ClientEvents.tick(event => {
  if (!showcaseStarted) {
    return;
  }

  showcaseTick++;
  if (showcaseTick < SHOWCASE_INTERVAL_TICKS) {
    return;
  }

  showcaseTick = 0;
  BubbleAnyway.showJson(JSON.stringify(SHOWCASE[showcaseIndex]));
  showcaseIndex = (showcaseIndex + 1) % SHOWCASE.length;
});

// To stop the currently visible bubble from another client script:
// BubbleAnyway.clear();
