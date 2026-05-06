export function Pagination({ page, totalPages, onPageChange }) {
  if (!totalPages || totalPages < 1) return null

  const pages = []
  const delta = 2
  const left = Math.max(0, page - delta)
  const right = Math.min(totalPages - 1, page + delta)

  if (left > 0) pages.push(0)
  if (left > 1) pages.push('...')
  for (let i = left; i <= right; i++) pages.push(i)
  if (right < totalPages - 2) pages.push('...')
  if (right < totalPages - 1) pages.push(totalPages - 1)

  return (
    <nav className="pagination" aria-label="Page navigation">
      <span className="pagination-label">Page {page + 1} of {totalPages}</span>
      <button
        className="pagination-btn"
        type="button"
        disabled={page === 0}
        onClick={() => onPageChange(page - 1)}
        aria-label="Previous page"
      >
        &#8249;
      </button>
      {pages.map((p, i) =>
        p === '...' ? (
          <span key={`ellipsis-${i}`} className="pagination-ellipsis">…</span>
        ) : (
          <button
            key={p}
            className={`pagination-btn${p === page ? ' pagination-btn-active' : ''}`}
            type="button"
            onClick={() => onPageChange(p)}
            aria-label={`Page ${p + 1}`}
            aria-current={p === page ? 'page' : undefined}
          >
            {p + 1}
          </button>
        )
      )}
      <button
        className="pagination-btn"
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
        aria-label="Next page"
      >
        &#8250;
      </button>
    </nav>
  )
}