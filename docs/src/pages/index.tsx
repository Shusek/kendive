import clsx from 'clsx';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import HomepageFeatures from '@site/src/components/HomepageFeatures';

import styles from './index.module.css';

function HomepageHeader() {
  return (
    <header className={clsx('hero', styles.heroBanner)}>
      <div className="container">
        <h1 className={styles.heroTitle}>Kotlin Runtime Web Assembly</h1>
        <p className={clsx('hero__subtitle', styles.heroSubtitle)}>
          JVM-native WebAssembly runtime for Kotlin-first plugin execution.
        </p>
        <div className={styles.buttons}>
          <Link
            className="button button--primary button--lg"
            to="/docs">
            Get Started
          </Link>
          <Link
            className="button button--outline button--lg"
            to="https://github.com/Shusek/kotlin-runtime-web-assembly"
            style={{marginLeft: '1rem'}}>
            GitHub
          </Link>
        </div>
      </div>
    </header>
  );
}

export default function Home(): JSX.Element {
  return (
    <Layout
      title="JVM-native WebAssembly runtime"
      description="Kotlin Runtime Web Assembly is a JVM native WebAssembly runtime with zero native dependencies.">
      <HomepageHeader />
      <main>
        <HomepageFeatures />
      </main>
    </Layout>
  );
}
