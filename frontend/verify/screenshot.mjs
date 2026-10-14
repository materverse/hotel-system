// 酒店系统页面渲染自检：用 Edge（playwright-core 驱动）登录后逐页截图，检查白屏与脚本错误
// 用法（在 frontend 目录下）：node verify/screenshot.mjs
// 依赖：frontend/node_modules/playwright-core
import { chromium } from 'playwright-core'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const ROOT = path.resolve(HERE, '..', '..')
const BASE = process.env.HOTEL_BASE || 'http://localhost:8080'
const EDGE = process.env.HOTEL_EDGE || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const OUT = path.join(ROOT, 'docs', 'screenshots')
fs.mkdirSync(OUT, { recursive: true })

const PAGES = [
  { name: '01-酒店首页', url: '/', auth: null },
  { name: '02-登录页', url: '/login', auth: null },
  { name: '03-客人自助注册', url: '/register', auth: null },
  { name: '04-房型与房价', url: '/rooms', auth: null },
  { name: '05-AI前台助手', url: '/ai', auth: 'frontdesk' },
  { name: '06-客人中心', url: '/guest', auth: 'guest' },
  { name: '07-我的预订', url: '/guest/reservations', auth: 'guest' },
  { name: '08-我的账单', url: '/guest/bills', auth: 'guest' },
  { name: '09-预存与消费流水', url: '/guest/transactions', auth: 'guest' },
  { name: '10-前台看板', url: '/admin/board', auth: 'frontdesk' },
  { name: '11-预订管理', url: '/admin/reservations', auth: 'frontdesk' },
  { name: '12-客房管理', url: '/admin/rooms', auth: 'frontdesk' },
  { name: '13-客人管理', url: '/admin/guests', auth: 'admin' },
  { name: '14-账单结算', url: '/admin/bills', auth: 'frontdesk' },
  { name: '15-消费项目', url: '/admin/consume-items', auth: 'admin' },
  { name: '16-房价策略', url: '/admin/rate-plans', auth: 'admin' },
  { name: '17-数据概览', url: '/admin/dashboard', auth: 'admin' },
  { name: '18-房态统计', url: '/admin/room-stats', auth: 'admin' },
  { name: '19-财务报表', url: '/admin/finance', auth: 'admin' },
  { name: '20-AI知识库', url: '/admin/knowledge', auth: 'admin' },
  { name: '21-AI评测', url: '/admin/ai-eval', auth: 'admin' },
  { name: '22-员工与权限', url: '/admin/users', auth: 'admin' }
]

const ACCOUNTS = {
  admin: { username: 'admin', password: '123456' },
  frontdesk: { username: 'frontdesk01', password: '123456' },
  guest: { username: 'guest01', password: '123456' }
}

async function login(page, account) {
  const res = await page.evaluate(async (acc) => {
    const r = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(acc)
    })
    return await r.json()
  }, account)
  if (res.code !== 200) throw new Error('登录失败：' + JSON.stringify(res))
  const data = res.data || {}
  // 兼容扁平与嵌套两种返回结构
  const flat = data.user ? { ...data.user, token: data.token, guestId: data.guestId, staffId: data.staffId } : data
  await page.evaluate((d) => {
    localStorage.setItem('hotel_token', d.token)
    localStorage.setItem('hotel_user', JSON.stringify(d))
    // 兼容可能的键名
    localStorage.setItem('gym_token', d.token)
    localStorage.setItem('gym_user', JSON.stringify(d))
  }, flat)
  return flat
}

const results = []
const browser = await chromium.launch({ executablePath: EDGE, headless: true })
try {
  for (const item of PAGES) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
    const page = await context.newPage()
    const errors = []
    page.on('pageerror', (e) => errors.push(String(e.message)))
    page.on('console', (m) => {
      if (m.type() === 'error') errors.push('console: ' + m.text())
    })
    try {
      await page.goto(BASE + '/login', { waitUntil: 'networkidle' })
      if (item.auth) await login(page, ACCOUNTS[item.auth])
      const response = await page.goto(BASE + item.url, { waitUntil: 'networkidle' })
      await page.waitForTimeout(1500)
      const text = (await page.textContent('body')) || ''
      const file = path.join(OUT, item.name + '.png')
      await page.screenshot({ path: file, fullPage: true })
      results.push({
        page: item.name,
        url: item.url,
        httpStatus: response ? response.status() : null,
        textLength: text.length,
        blank: text.trim().length < 40,
        errors: errors.filter((e) => !e.includes('favicon')).slice(0, 3),
        shot: path.relative(ROOT, file)
      })
    } catch (e) {
      results.push({ page: item.name, url: item.url, failed: String(e.message), errors })
    } finally {
      await context.close()
    }
  }
} finally {
  await browser.close()
}

fs.writeFileSync(path.join(OUT, 'verify.json'), JSON.stringify(results, null, 2), 'utf-8')
let bad = 0
for (const r of results) {
  const flag = r.failed ? 'FAIL' : r.blank ? 'BLANK' : r.errors.length ? 'ERR' : 'OK'
  if (flag !== 'OK') bad++
  console.log(`${flag.padEnd(5)} ${r.page}  http=${r.httpStatus ?? '-'}  text=${r.textLength ?? '-'}  ${r.failed || (r.errors || []).join(' | ') || ''}`)
}
console.log(`\n共 ${results.length} 页，异常 ${bad} 页，截图目录 docs/screenshots`)
