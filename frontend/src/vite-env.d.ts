/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  /**
   * Development-only: renders the checkout with clearly-labelled mock
   * responses. Never enable this for a real payment run.
   */
  readonly VITE_CHECKOUT_MOCK?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
