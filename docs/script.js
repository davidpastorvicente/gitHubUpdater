const faqItems = Array.from(document.querySelectorAll('.faq-item'));

function openFaq(item) {
  const button = item.querySelector('.faq-question');
  const answer = item.querySelector('.faq-answer');

  if (!button || !answer) return;

  answer.hidden = false;
  const targetHeight = answer.scrollHeight;
  answer.style.height = '0px';
  answer.style.opacity = '0';

  requestAnimationFrame(() => {
    button.setAttribute('aria-expanded', 'true');
    item.classList.add('is-open');
    answer.style.height = `${targetHeight}px`;
    answer.style.opacity = '1';
  });

  const onTransitionEnd = (event) => {
    if (event.propertyName !== 'height') return;
    answer.style.height = 'auto';
    answer.removeEventListener('transitionend', onTransitionEnd);
  };

  answer.addEventListener('transitionend', onTransitionEnd);
}

function closeFaq(item) {
  const button = item.querySelector('.faq-question');
  const answer = item.querySelector('.faq-answer');

  if (!button || !answer) return;

  answer.style.height = `${answer.scrollHeight}px`;
  answer.style.opacity = '1';

  requestAnimationFrame(() => {
    button.setAttribute('aria-expanded', 'false');
    item.classList.remove('is-open');
    answer.style.height = '0px';
    answer.style.opacity = '0';
  });

  const onTransitionEnd = (event) => {
    if (event.propertyName !== 'height') return;
    answer.hidden = true;
    answer.removeEventListener('transitionend', onTransitionEnd);
  };

  answer.addEventListener('transitionend', onTransitionEnd);
}

faqItems.forEach((item) => {
  const button = item.querySelector('.faq-question');

  if (!button) return;

  button.addEventListener('click', () => {
    const isOpen = button.getAttribute('aria-expanded') === 'true';
    faqItems.forEach((otherItem) => {
      if (otherItem !== item && otherItem.classList.contains('is-open')) {
        closeFaq(otherItem);
      }
    });

    if (isOpen) {
      closeFaq(item);
    } else {
      openFaq(item);
    }
  });
});

const screenshotFrames = Array.from(document.querySelectorAll('.screenshot-frame'));

screenshotFrames.forEach((frame) => {
  const image = frame.querySelector('img');
  if (!image) return;

  image.addEventListener('load', () => {
    frame.classList.remove('placeholder');
  });

  image.addEventListener('error', () => {
    frame.classList.add('placeholder');
  });

  if (image.complete && image.naturalWidth > 0) {
    frame.classList.remove('placeholder');
  }
});
