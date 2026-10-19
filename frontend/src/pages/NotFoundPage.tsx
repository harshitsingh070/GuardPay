import { Link } from 'react-router-dom';
import { usePageTitle } from '@/hooks/usePageTitle';

export default function NotFoundPage() {
  usePageTitle('Not found');
  return (
    <section>
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <p className="mt-2 text-muted-foreground">
        This route does not exist yet.{' '}
        <Link to="/" className="underline">
          Go home
        </Link>
      </p>
    </section>
  );
}
