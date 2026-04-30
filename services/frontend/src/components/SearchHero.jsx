export function SearchHero({ onSearch, defaultValue = '' }) {
  return (
    <section className="hero-stage">
      <div className="hero-grid">
        <div className="hero-copy retail-panel hero-surface">
          <p className="eyebrow">Spring savings</p>
          <h1>Save on everyday essentials, home upgrades, and more.</h1>
          <p className="hero-text">
            Shop popular picks, discover new arrivals, and choose pickup or shipping at checkout.
          </p>
          <form
            className="search-bar"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              onSearch(String(form.get('q') || '').trim())
            }}
          >
            <input name="q" defaultValue={defaultValue} placeholder="Search everything at BlueMart online and in store" />
            <button type="submit">Search</button>
          </form>
          <div className="hero-badges">
            <span className="feature-badge">Pickup today</span>
            <span className="feature-badge">Fast delivery</span>
            <span className="feature-badge">Low prices every day</span>
          </div>
        </div>
        <div className="hero-side stack-md">
          <article className="hero-tile hero-tile-blue">
            <span className="hero-kicker">Top picks</span>
            <strong>Fresh finds for home, health, groceries, and tools.</strong>
          </article>
          <article className="hero-tile hero-tile-amber">
            <span className="hero-kicker">Pickup today</span>
            <strong>Choose pickup at checkout for eligible items.</strong>
          </article>
          <article className="hero-tile hero-tile-plain">
            <span className="hero-kicker">More to explore</span>
            <strong>Browse featured deals, trending brands, and saved items.</strong>
          </article>
        </div>
      </div>
      <div className="hero-strip">
        <div><strong>Free pickup</strong><span>On eligible items from your local store.</span></div>
        <div><strong>Delivery</strong><span>Get everyday essentials sent to your saved address.</span></div>
        <div><strong>Easy returns</strong><span>Free 90-day returns on most items.</span></div>
      </div>
    </section>
  )
}
