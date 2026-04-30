import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const outDir = path.resolve(root, '../account-service/src/main/resources/static')
const indexPath = path.join(outDir, 'index.html')

const pages = [
  'account.html',
  'cart.html',
  'checkout.html',
  'confirmation.html',
  'order-failed.html',
  'order-status.html',
  'product.html',
  'signin.html',
  'signup.html',
  'wishlist.html',
]

const indexHtml = fs.readFileSync(indexPath, 'utf8')
for (const page of pages) {
  fs.writeFileSync(path.join(outDir, page), indexHtml)
}
