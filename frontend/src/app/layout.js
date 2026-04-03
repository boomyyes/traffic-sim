import './globals.css';

export const metadata = {
  title: 'Indian Traffic Simulation Engine',
  description: 'A microscopic simulation engine using Strip-Based lane-free logic for heterogeneous Indian traffic environments.',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
