/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_ROOT?: string
  readonly VITE_WS_ROOT?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
