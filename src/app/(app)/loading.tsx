// Instant route-transition skeleton for every (app) page.
// Renders the moment a tab is tapped, while the server fetches data.
export default function AppLoading() {
  return (
    <div className="mx-auto w-full max-w-lg space-y-4 px-4 pb-24 pt-6 animate-pulse">
      {/* Header row */}
      <div className="flex items-center justify-between">
        <div className="space-y-2">
          <div className="h-5 w-36 rounded-lg bg-white/10" />
          <div className="h-3 w-24 rounded-lg bg-white/5" />
        </div>
        <div className="h-10 w-10 rounded-full bg-white/10" />
      </div>

      {/* Hero card (score ring area) */}
      <div className="nafs-card flex items-center justify-center p-6">
        <div className="h-32 w-32 rounded-full border-8 border-white/10" />
      </div>

      {/* Content cards */}
      {[0, 1, 2].map((i) => (
        <div key={i} className="nafs-card space-y-3 p-4">
          <div className="h-4 w-1/3 rounded-lg bg-white/10" />
          <div className="h-3 w-2/3 rounded-lg bg-white/5" />
          <div className="h-3 w-1/2 rounded-lg bg-white/5" />
        </div>
      ))}
    </div>
  )
}
