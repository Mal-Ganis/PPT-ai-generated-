const POST_LOGIN_KEY = 'ppt_post_login';
const HERO_INTRO_KEY = 'ppt_hero_intro_played';

export function markPostLoginEntrance(): void {
  sessionStorage.setItem(POST_LOGIN_KEY, '1');
}

export function consumePostLoginEntrance(): boolean {
  const value = sessionStorage.getItem(POST_LOGIN_KEY) === '1';
  if (value) {
    sessionStorage.removeItem(POST_LOGIN_KEY);
  }
  return value;
}

export function shouldPlayHeroIntro(): boolean {
  if (sessionStorage.getItem(POST_LOGIN_KEY) === '1') {
    return false;
  }
  if (sessionStorage.getItem(HERO_INTRO_KEY) === '1') {
    return false;
  }
  sessionStorage.setItem(HERO_INTRO_KEY, '1');
  return true;
}
