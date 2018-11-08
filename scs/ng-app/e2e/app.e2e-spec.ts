import { ScsPage } from './app.po';

describe('scs App', () => {
  let page: ScsPage;

  beforeEach(() => {
    page = new ScsPage();
  });

  it('should display message saying app works', () => {
    page.navigateTo();
    expect(page.getParagraphText()).toEqual('app works!');
  });
});
