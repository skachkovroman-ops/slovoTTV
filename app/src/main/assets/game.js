// Чистая логика игры (без DOM) — можно тестировать в Node.
const WORD_LEN = 5;
const MAX_TRIES = 5;

// Возвращает массив из 5 значений: 'g' — на своём месте (зелёный),
// 'y' — есть в слове, но в другом месте (жёлтый), 'b' — нет в слове (коричневый).
// Повторяющиеся буквы учитываются корректно: жёлтых не больше, чем таких букв в загаданном слове.
function evaluate(guess, answer) {
  const res = new Array(WORD_LEN).fill('b');
  const left = {};
  for (let i = 0; i < WORD_LEN; i++) {
    if (guess[i] === answer[i]) res[i] = 'g';
    else left[answer[i]] = (left[answer[i]] || 0) + 1;
  }
  for (let i = 0; i < WORD_LEN; i++) {
    if (res[i] === 'g') continue;
    if (left[guess[i]] > 0) { res[i] = 'y'; left[guess[i]]--; }
  }
  return res;
}

if (typeof module !== 'undefined') module.exports = { evaluate, WORD_LEN, MAX_TRIES };
