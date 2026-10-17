import '@testing-library/jest-dom/vitest';

// jsdom lacks these browser APIs used by the views.
Element.prototype.scrollIntoView = () => {};
if (!window.matchMedia) {
  window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {} });
}
