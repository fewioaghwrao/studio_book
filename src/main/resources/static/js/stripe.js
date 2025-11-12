// /public/js/stripe.js
document.addEventListener("DOMContentLoaded", async () => {
  const btn = document.getElementById("paymentButton");
  if (!btn) return;

  const publishableKey = window.STRIPE_PK;
  const sessionId = btn.dataset.sessionId || window.__SESSION_ID__;

  if (!publishableKey || !sessionId) {
    console.error("Stripe publishable key or sessionId is missing.");
    return;
  }

  const stripe = Stripe(publishableKey);
  btn.addEventListener("click", async () => {
    btn.disabled = true;
    try {
      const { error } = await stripe.redirectToCheckout({ sessionId });
      if (error) {
        console.error(error);
        alert("決済画面への遷移に失敗しました。時間をおいて再度お試しください。");
      }
    } finally {
      btn.disabled = false;
    }
  });
});
