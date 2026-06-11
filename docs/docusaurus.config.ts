import { themes as prismThemes } from 'prism-react-renderer';
import type { Config } from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'Kotlin Runtime Web Assembly',
  tagline: 'Kotlin Runtime Web Assembly, a JVM native WebAssembly runtime',

  // Set the production url of your site here
  url: 'https://shusek.github.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/kotlin-runtime-web-assembly/',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'Shusek', // Usually your GitHub org/user name.
  projectName: 'kotlin-runtime-web-assembly', // Usually your repo name.

  // Keep this as warn while migration pages and redirects are still being consolidated.
  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  plugins: [
    [
      '@docusaurus/plugin-client-redirects',
      {
        redirects: [
          {
            to: '/docs/annotations',
            from: ['/docs/experimental/host-modules', '/docs/usage/annotations'],
          },
          {
            to: '/docs/execution/runtime-compiler',
            from: ['/docs/experimental/aot', '/docs/usage/runtime-compiler'],
          },
          {
            to: '/docs/execution/build-time-compiler',
            from: ['/docs/usage/build-time-compiler'],
          },
          {
            to: '/docs/core/host-functions',
            from: ['/docs/usage/host-functions'],
          },
          {
            to: '/docs/core/memory',
            from: ['/docs/usage/memory'],
          },
          {
            to: '/docs/core/linking',
            from: ['/docs/usage/linking'],
          },
          {
            to: '/docs/core/execution-modes',
            from: ['/docs/usage/execution_modes'],
          },
          {
            to: '/docs/wasi',
            from: ['/docs/usage/wasi'],
          },
          {
            to: '/docs/advanced/cpu-limits',
            from: ['/docs/usage/cpu'],
          },
          {
            to: '/docs/advanced/simd',
            from: ['/docs/usage/simd'],
          },
          {
            to: '/docs/advanced/tools',
            from: ['/docs/usage/tools'],
          },
          {
            to: '/docs/advanced/logging',
            from: ['/docs/usage/logging'],
          },
          {
            to: '/docs/getting-started/installation',
            from: ['/docs/usage/bom'],
          },
          {
            to: '/docs/advanced/memory-customization',
            from: ['/docs/advanced/memory'],
          },
          {
            to: '/docs/execution/compiler-cache',
            from: ['/docs/experimental/runtime-compiler-cache'],
          },
        ],
      },
    ],
  ],
  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
        },
        blog: {
          showReadingTime: true,
          feedOptions: {
            type: ['rss', 'atom'],
            xslt: true,
          },
          // Useful options to enforce blogging best practices
          onInlineTags: 'warn',
          onInlineAuthors: 'warn',
          onUntruncatedBlogPosts: 'warn',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    navbar: {
      title: 'Kotlin Runtime Web Assembly',
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: 'Docs',
        },
        { to: '/blog', label: 'Blog', position: 'left' },
        {
          href: 'https://github.com/Shusek/kotlin-runtime-web-assembly',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {
              label: 'Docs',
              to: '/docs',
            },
          ],
        },
        {
          title: 'More',
          items: [
            {
              label: 'Blog',
              to: '/blog',
            },
            {
              label: 'GitHub',
              href: 'https://github.com/Shusek/kotlin-runtime-web-assembly',
            },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Shusek. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['java'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
