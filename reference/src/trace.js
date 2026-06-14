import { writeFileSync } from "fs"
const dv = new DataView(new ArrayBuffer(8))
export const bits = (v) => {
  dv.setFloat64(0, v)
  return dv.getBigUint64(0).toString(16)
}
let lines = []
export const trace = (s) => {
  lines.push(s)
}
export const flush = (file) => {
  writeFileSync(file, lines.join("\n") + "\n")
  lines = []
}
