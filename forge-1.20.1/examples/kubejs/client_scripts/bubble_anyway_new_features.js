// Bubble Anyway Forge 1.20.1 feature showcase.
// Copy to kubejs/client_scripts and join a world. Each entry tests one feature.
const BubbleAnyway = Java.loadClass('com.bubbleanyway.kubejs.BubbleKubeJSBindings');

const TEST_INTERVAL = 90;
const TESTS = [
  {
    id: 'test_plain_multiline',
    text: '普通多行文本\n第二行会参与自动高度计算',
    icon: 'minecraft:diamond',
    iconType: 'ITEM',
    iconSize: 22,
    anchor: 'TOP_LEFT',
    x: 10,
    y: 10,
    animation: 'SLIDE_FROM_LEFT',
    duration: 80,
    fadeIn: 8,
    fadeOut: 12,
    slideIn: 10,
    slideOut: 12,
    background: 'bubble_anyway:textures/gui/background.png',
    backgroundBorder: 8,
    backgroundGuide: 1,
    replace: true
  },
  {
    id: 'test_rich_text_parts',
    textParts: [
      { role: 'title', text: '标题：颜色、粗体和较大字号' },
      { role: 'subtitle', text: '\n副标题：蓝色、斜体和较小字号' }
    ],
    textStyles: {
      title: { color: '#FFFFFFFF', bold: true, scale: 1.15 },
      subtitle: { color: '#55FFFF', italic: true, scale: 0.9, shadow: false }
    },
    icon: 'minecraft:emerald',
    iconType: 'ITEM',
    iconSize: 24,
    iconGap: 10,
    textAlign: 'CENTER',
    anchor: 'CENTER_TOP',
    y: 18,
    width: 300,
    padding: 12,
    animation: 'SLIDE_FROM_TOP',
    duration: 80,
    fadeIn: 8,
    fadeOut: 12,
    slideIn: 10,
    slideOut: 10,
    background: 'bubble_anyway:textures/gui/background_modern.png',
    backgroundBorder: 8,
    backgroundGuide: 1,
    replace: true
  },
  {
    id: 'test_texture_icon',
    text: 'PNG 纹理图标：使用原版钻石纹理',
    icon: 'minecraft:textures/item/diamond.png',
    iconType: 'TEXTURE',
    iconSize: 30,
    iconOffsetX: 2,
    iconOffsetY: -2,
    textOffsetX: 4,
    textOffsetY: 1,
    anchor: 'CENTER_BOTTOM',
    y: 18,
    animation: 'SLIDE_FROM_BOTTOM',
    duration: 80,
    fadeIn: 8,
    fadeOut: 12,
    slideIn: 8,
    slideOut: 12,
    background: 'bubble_anyway:textures/gui/background.png',
    backgroundBorder: 8,
    backgroundGuide: 1,
    replace: true
  },
  {
    id: 'test_slide_without_fade',
    text: '弹入但不淡入淡出\nfadeIn=0 fadeOut=0',
    anchor: 'CENTER_LEFT',
    x: 10,
    y: 0,
    animation: 'SLIDE_FROM_LEFT',
    fadeIn: 0,
    fadeOut: 0,
    slideIn: 12,
    slideOut: 12,
    duration: 80,
    backgroundColor: '#E62E7D32',
    replace: true
  },
  {
    id: 'test_fade_only',
    text: '只有淡入淡出\nslideIn 和 slideOut 在 FADE 模式下无效',
    anchor: 'CENTER_RIGHT',
    x: -10,
    y: 0,
    animation: 'FADE',
    fadeIn: 18,
    fadeOut: 18,
    slideIn: 30,
    slideOut: 30,
    duration: 80,
    backgroundColor: '#E64A235A',
    textAlign: 'RIGHT',
    replace: true
  },
  {
    id: 'test_auto_wrap',
    text: '这是一段很长的文本，用来测试自动换行、自动高度、图标列宽以及九宫格背景在不同长度下是否保持连续。',
    icon: 'minecraft:clock',
    iconType: 'ITEM',
    maxWidth: 220,
    iconSize: 18,
    iconGap: 8,
    anchor: 'CENTER',
    textAlign: 'LEFT',
    padding: 10,
    animation: 'FADE',
    duration: 80,
    fadeIn: 8,
    fadeOut: 12,
    background: 'bubble_anyway:textures/gui/background.png',
    backgroundBorder: 8,
    backgroundGuide: 1,
    replace: true
  },
  {
    id: 'test_theme_override',
    theme: 'bubble_anyway:test_rich',
    textParts: [
      { role: 'title', text: '本地主题 + textParts 覆盖' },
      { role: 'subtitle', text: '\n只在当前气泡覆盖文本内容' }
    ],
    icon: 'minecraft:nether_star',
    iconType: 'ITEM',
    priority: 260
  }
];

let tick = 0;
let index = 0;
let running = false;

ClientEvents.loggedIn(event => {
  tick = 0;
  index = 0;
  running = true;
});

ClientEvents.tick(event => {
  if (!running || ++tick < TEST_INTERVAL) {
    return;
  }

  tick = 0;
  BubbleAnyway.showJson(JSON.stringify(TESTS[index]));
  index = (index + 1) % TESTS.length;
});

// To stop the showcase from another client script:
// running = false;
// BubbleAnyway.clear();
