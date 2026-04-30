import { useMemo } from 'react'
import { useQueries } from '@tanstack/react-query'
import { api } from './api'

export function useProductLookupBySku(skus = []) {
  const uniqueSkus = useMemo(
    () => [...new Set(skus.filter(Boolean))],
    [skus],
  )

  const queries = useQueries({
    queries: uniqueSkus.map((sku) => ({
      queryKey: ['catalog-item', sku],
      queryFn: () => api.getItemBySku(sku),
      enabled: Boolean(sku),
      staleTime: 300_000,
    })),
  })

  return useMemo(() => {
    const lookup = {}
    queries.forEach((query, index) => {
      const sku = uniqueSkus[index]
      if (sku && query.data) {
        lookup[sku] = query.data
      }
    })
    return lookup
  }, [queries, uniqueSkus])
}
