import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

// shadcn/ui-style helper (no UI primitives implemented yet).
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
