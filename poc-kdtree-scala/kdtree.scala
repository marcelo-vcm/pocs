import java.io.PrintWriter
import java.nio.file.{Files, Paths}

// ─────────────────────────────────────────────────────────────
//  2-D K-D Tree – step-by-step visualiser  (median build)
//  Run:  scala-cli kdtree.scala
// ─────────────────────────────────────────────────────────────

// ── ANSI helpers ─────────────────────────────────────────────
object Ansi:
  val RST   = "\u001b[0m"
  val BOLD  = "\u001b[1m"
  val DIM   = "\u001b[2m"
  val RED   = "\u001b[31m"
  val GRN   = "\u001b[32m"
  val YLW   = "\u001b[33m"
  val BLU   = "\u001b[34m"
  val MAG   = "\u001b[35m"
  val CYN   = "\u001b[36m"
  val WHT   = "\u001b[37m"
  val BWHT  = "\u001b[97m"
  val CLEAR = "\u001b[2J\u001b[H"

  private val PT_COLORS = Array(GRN, YLW, CYN, MAG, "\u001b[91m", "\u001b[92m", "\u001b[93m", "\u001b[96m")
  def ptColor(i: Int): String = PT_COLORS(i % PT_COLORS.length)

import Ansi._

// ── Core data types ───────────────────────────────────────────
case class Point(x: Int, y: Int, name: Char, idx: Int)
case class Region(x1: Int, y1: Int, x2: Int, y2: Int)
case class SplitLine(value: Int, dim: Int, region: Region)

sealed trait KDTree
case object Empty                                                             extends KDTree
case class Node(pt: Point, left: KDTree, right: KDTree, dim: Int, dep: Int) extends KDTree

// ── Build step (one recursive call of the median algorithm) ──
case class BuildStep(
  subset:  List[Point],  // points in this subregion (original order)
  sorted:  List[Point],  // sorted by dim
  medIdx:  Int,          // index of median in `sorted`
  dim:     Int,          // 0=X, 1=Y
  region:  Region,
  depth:   Int
)

// ── Median-based k-d tree construction ───────────────────────
object KDTree:

  /** Build the full tree using median selection. */
  def build(pts: List[Point], depth: Int = 0, reg: Region = Region(0, 0, 100, 100)): KDTree =
    if pts.isEmpty then Empty
    else
      val dim    = depth % 2
      val sorted = if dim == 0 then pts.sortBy(_.x) else pts.sortBy(_.y)
      val mi     = (sorted.length - 1) / 2
      val med    = sorted(mi)
      val (lReg, rReg) = subRegions(dim, med, reg)
      Node(med, build(sorted.take(mi), depth + 1, lReg),
                build(sorted.drop(mi + 1), depth + 1, rReg), dim, depth)

  /** Collect split lines from the tree together with their regions. */
  def splits(tree: KDTree, reg: Region = Region(0, 0, 100, 100)): List[SplitLine] = tree match
    case Empty => Nil
    case Node(p, l, r, dim, _) =>
      val line = SplitLine(if dim == 0 then p.x else p.y, dim, reg)
      val (lReg, rReg) = subRegions(dim, p, reg)
      line :: splits(l, lReg) ::: splits(r, rReg)

  /** Return the first `n` nodes (DFS pre-order) of the tree. */
  def partial(tree: KDTree, n: Int): KDTree =
    def go(t: KDTree, rem: Int): (KDTree, Int) =
      if rem <= 0 then (Empty, 0)
      else t match
        case Empty => (Empty, rem)
        case Node(p, l, r, dim, dep) =>
          val (newL, r1) = go(l, rem - 1)
          val (newR, r2) = go(r, r1)
          (Node(p, newL, newR, dim, dep), r2)
    go(tree, n)._1

  private def subRegions(dim: Int, p: Point, reg: Region): (Region, Region) =
    if dim == 0 then (reg.copy(x2 = p.x), reg.copy(x1 = p.x))
    else             (reg.copy(y1 = p.y), reg.copy(y2 = p.y))

/** Generate every recursive call as a BuildStep in DFS order. */
def allBuildSteps(
  pts:   List[Point],
  depth: Int   = 0,
  reg:   Region = Region(0, 0, 100, 100)
): List[BuildStep] =
  if pts.isEmpty then Nil
  else
    val dim    = depth % 2
    val sorted = if dim == 0 then pts.sortBy(_.x) else pts.sortBy(_.y)
    val mi     = (sorted.length - 1) / 2
    val med    = sorted(mi)
    val (lReg, rReg) =
      if dim == 0 then (reg.copy(x2 = med.x), reg.copy(x1 = med.x))
      else             (reg.copy(y1 = med.y), reg.copy(y2 = med.y))
    BuildStep(pts, sorted, mi, dim, reg, depth) ::
      allBuildSteps(sorted.take(mi),      depth + 1, lReg) :::
      allBuildSteps(sorted.drop(mi + 1),  depth + 1, rReg)

// ── Cartesian plane renderer ──────────────────────────────────
object Plane:
  val GW = 64
  val GH = 28

  def gx(wx: Int): Int = 1 + ((wx.toDouble / 100) * (GW - 2)).toInt.min(GW - 2).max(1)
  def gy(wy: Int): Int = (GH - 2) - ((wy.toDouble / 100) * (GH - 2)).toInt.min(GH - 2).max(0)

  def render(
    allPts:     List[Point],
    splitLines: List[SplitLine],
    activeReg:  Option[Region],
    medianPt:   Option[Point],
    useColor:   Boolean
  ): String =
    val ch  = Array.fill(GH, GW)(' ')
    val col = Array.fill(GH, GW)("")

    def set(y: Int, x: Int, c: Char, k: String = "") =
      if x > 0 && x < GW - 1 && y > 0 && y < GH - 1 then
        ch(y)(x) = c; if useColor then col(y)(x) = k

    // outer border
    for x <- 0 until GW do { ch(0)(x) = '-'; ch(GH-1)(x) = '-' }
    for y <- 0 until GH do { ch(y)(0) = '|'; ch(y)(GW-1) = '|' }
    ch(0)(0) = '+'; ch(0)(GW-1) = '+'; ch(GH-1)(0) = '+'; ch(GH-1)(GW-1) = '+'

    // axis ticks
    for wx <- 0 to 100 by 10 do ch(GH-1)(gx(wx)) = '+'
    for wy <- 0 to 100 by 10 do ch(gy(wy))(0)     = '+'

    // active region highlight (drawn before split lines so they overwrite)
    activeReg.foreach: r =>
      val rx1 = gx(r.x1).max(1); val rx2 = gx(r.x2).min(GW-2)
      val ry1 = gy(r.y2).max(1); val ry2 = gy(r.y1).min(GH-2)
      for x <- rx1 to rx2 do
        if ch(ry1)(x) == ' ' then set(ry1, x, '~', DIM + YLW)
        if ch(ry2)(x) == ' ' then set(ry2, x, '~', DIM + YLW)
      for y <- ry1 to ry2 do
        if ch(y)(rx1) == ' ' then set(y, rx1, '!', DIM + YLW)
        if ch(y)(rx2) == ' ' then set(y, rx2, '!', DIM + YLW)

    // split lines
    for sl <- splitLines do
      if sl.dim == 0 then
        val x  = gx(sl.value)
        val y1 = gy(sl.region.y2).max(1)
        val y2 = gy(sl.region.y1).min(GH-2)
        for y <- y1 to y2 if ch(y)(x) == ' ' do set(y, x, ':', RED)
      else
        val y  = gy(sl.value)
        val x1 = gx(sl.region.x1).max(1)
        val x2 = gx(sl.region.x2).min(GW-2)
        for x <- x1 to x2 if ch(y)(x) == ' ' do set(y, x, '.', BLU)

    // all points (dimmed if not yet added as a node)
    for p <- allPts do
      val (px, py) = (gx(p.x), gy(p.y))
      if px > 0 && px < GW-1 && py > 0 && py < GH-1 then
        ch(py)(px) = p.name
        col(py)(px) = if useColor then DIM + WHT else ""

    // override color for the median (bright white, bold)
    medianPt.foreach: p =>
      val (px, py) = (gx(p.x), gy(p.y))
      if useColor then col(py)(px) = BOLD + BWHT

    val sb = new StringBuilder
    for y <- 0 until GH do
      for x <- 0 until GW do
        val c = ch(y)(x); val k = col(y)(x)
        if useColor && k.nonEmpty then sb.append(k).append(c).append(RST)
        else sb.append(c)
      sb.append('\n')

    sb.append(' ')
    val lbl = Array.fill(GW)(' ')
    for wx <- 0 to 100 by 10 do
      val x = gx(wx); val s = wx.toString
      for (c, i) <- s.zipWithIndex if x - 1 + i < GW do lbl(x - 1 + i) = c
    sb.append(lbl.mkString).append('\n')
    sb.append(s"  ${if useColor then RED  + ":" + RST else ":"}= X-split   ")
    sb.append(s"${if useColor  then BLU  + "." + RST else "."}= Y-split   ")
    sb.append(s"${if useColor  then YLW + "~!" + RST else "~!"}= active region\n")
    sb.toString

// ── Sort panel renderer ───────────────────────────────────────
/** Render a box showing the unsorted input, sorted order, and
 *  left / median / right partition for one BuildStep. */
def sortPanel(s: BuildStep, useColor: Boolean): String =
  val dimName    = if s.dim == 0 then "X" else "Y"
  val dimCol     = if useColor then (if s.dim == 0 then RED else BLU) else ""
  val dimRST     = if useColor then RST else ""
  val W          = 72    // panel inner width

  def pLabel(p: Point, role: String): String =
    val coordVal = if s.dim == 0 then p.x else p.y
    val coordStr = if s.dim == 0 then s"${dimCol}${p.x}${dimRST},${p.y}"
                   else               s"${p.x},${dimCol}${p.y}${dimRST}"
    val nameStr  = if useColor then s"${BOLD}${ptColor(p.idx)}${p.name}${dimRST}" else p.name.toString
    role match
      case "med" => s"${if useColor then BOLD + BWHT else ""}[${p.name}(${if s.dim==0 then p.x else p.y})]${dimRST}"
      case _     => s"$nameStr(${coordStr})"

  val regionStr = s"x:[${s.region.x1},${s.region.x2}] y:[${s.region.y1},${s.region.y2}]"
  val depthStr  = s"depth ${s.depth}"

  val med    = s.sorted(s.medIdx)
  val left   = s.sorted.take(s.medIdx)
  val right  = s.sorted.drop(s.medIdx + 1)

  // unsorted row
  val unsorted = s.subset.map(p => pLabel(p, "")).mkString("  ")
  // sorted row
  val sortedRow =
    left.map(p => pLabel(p, "left")).mkString("  ") + "  " +
    pLabel(med, "med") + "  " +
    right.map(p => pLabel(p, "right")).mkString("  ")

  // arrow row (plain, no color)
  val lCount = left.size; val rCount = right.size
  val arrowRow =
    (if lCount > 0 then s"←── left($lCount) " else "") +
    "  median  " +
    (if rCount > 0 then s"right($rCount) ──→" else "")

  val border = "─" * W
  val sb = new StringBuilder

  def boxLine(content: String, label: String = "") =
    val inner = if label.isEmpty then content else s"$label $content"
    sb.append(s"│ $inner\n")

  sb.append(s"┌─ Sort by ${dimCol}${dimName}${dimRST}  $regionStr  $depthStr  ${s.subset.size} pts $border".take(W + 2)).append("┐\n")
  boxLine(unsorted,  "Input: ")
  boxLine(sortedRow, "Sorted:")
  boxLine(arrowRow,  "       ")
  sb.append(s"└$border┘\n")
  sb.toString

// ── ASCII tree renderer ───────────────────────────────────────
object TreePrinter:
  def lines(tree: KDTree, pfx: String = "", conn: String = "", useColor: Boolean = true): List[String] =
    tree match
      case Empty => Nil
      case Node(p, l, r, dim, _) =>
        val dimTxt = if useColor then (if dim == 0 then s"${RED}X${RST}" else s"${BLU}Y${RST}")
                     else             (if dim == 0 then "X" else "Y")
        val pTxt   = if useColor then s"${BOLD}${ptColor(p.idx)}${p.name}${RST}(${p.x},${p.y})"
                     else             s"${p.name}(${p.x},${p.y})"
        val node   = s"$pfx$conn[$dimTxt] $pTxt"
        val nPfx   = pfx + (conn match { case "├── " => "│   "; case "└── " => "    "; case _ => "" })
        node :: lines(r, nPfx, "├── ", useColor) ::: lines(l, nPfx, "└── ", useColor)

// ── Keyboard helper ───────────────────────────────────────────
def waitForSpace(): Unit =
  ProcessBuilder("sh", "-c", "stty raw -echo </dev/tty").inheritIO().start().waitFor()
  try
    val tty  = new java.io.FileInputStream("/dev/tty")
    var done = false
    while !done do
      val b = tty.read()
      if b == 32 || b == 13 || b == 10 || b == 'q' then done = true
    tty.close()
  finally
    ProcessBuilder("sh", "-c", "stty sane </dev/tty").inheritIO().start().waitFor()

// ── File save ─────────────────────────────────────────────────
def saveStep(dir: String, step: Int, bs: BuildStep, plane: String, sort: String, tree: List[String]): Unit =
  val med = bs.sorted(bs.medIdx)
  val pw  = new PrintWriter(s"$dir/step_${"%02d".format(step)}.txt")
  pw.println(s"=== Step $step: Median ${med.name}(${med.x},${med.y}) – depth ${bs.depth} ===\n")
  pw.println(sort)
  pw.println("Cartesian Plane:")
  pw.println(plane)
  pw.println("K-D Tree so far:")
  tree.foreach(pw.println)
  pw.close()

// ── Entry point ───────────────────────────────────────────────
@main def run(): Unit =
  val outDir = "kdtree_steps"
  Files.createDirectories(Paths.get(outDir))

  val rawPoints = List(
    (50, 75), (25, 40), (75, 25), (10, 60),
    (35, 90), (60, 10), (85, 55)
  )
  val names     = "ABCDEFG"
  val allPoints = rawPoints.zipWithIndex.map { case ((x, y), i) => Point(x, y, names(i), i) }

  // Pre-compute the full tree and all recursive build steps
  val fullTree = KDTree.build(allPoints)
  val steps    = allBuildSteps(allPoints)

  val logPw = new PrintWriter(s"$outDir/build_log.txt")
  logPw.println("K-D Tree Build Log  (median-based construction)")
  logPw.println("=" * 60)

  // ── intro screen ────────────────────────────────────────────
  print(CLEAR)
  println(s"${BOLD}${CYN}╔════════════════════════════════════════════╗")
  println(s"║   2-D K-D Tree – Median Build  (${allPoints.size} pts)   ║")
  println(s"╚════════════════════════════════════════════╝${RST}\n")

  println(s"${BOLD}All points:${RST}")
  println(s"  ${"Name".padTo(6, ' ')} ${"X".padTo(5,' ')} ${"Y".padTo(5,' ')}")
  println(s"  ${"─" * 18}")
  for p <- allPoints do
    val c = ptColor(p.idx)
    println(s"  ${if true then BOLD+c else ""}${p.name}${RST}      ${p.x.toString.padTo(5,' ')} ${p.y}")

  println(s"\n${WHT}Algorithm: sort by alternating axis, pick median as node, recurse.")
  println(s"Press SPACE to start building…${RST}\n")
  waitForSpace()

  // ── step loop ───────────────────────────────────────────────
  for (bs, stepIdx) <- steps.zipWithIndex do
    val stepNum  = stepIdx + 1
    val med      = bs.sorted(bs.medIdx)
    val partial  = KDTree.partial(fullTree, stepNum)
    val splitLns = KDTree.splits(partial)
    val treeLns  = TreePrinter.lines(partial, useColor = true)
    val treePlain= TreePrinter.lines(partial, useColor = false)

    val planeColor = Plane.render(allPoints, splitLns, Some(bs.region), Some(med), useColor = true)
    val planePlain = Plane.render(allPoints, splitLns, None,            None,      useColor = false)
    val sortColor  = sortPanel(bs, useColor = true)
    val sortPlain  = sortPanel(bs, useColor = false)

    // ── terminal output ──────────────────────────────────────
    print(CLEAR)
    val dimName = if bs.dim == 0 then s"${RED}X${RST}" else s"${BLU}Y${RST}"
    println(s"${BOLD}${CYN}═══ Step $stepNum / ${steps.size} ═══  " +
            s"Node: ${BOLD}${ptColor(med.idx)}${med.name}${RST}${CYN}(${med.x},${med.y})  " +
            s"depth ${bs.depth}  split by $dimName${RST}\n")

    // Sort panel
    print(sortColor)

    // Plane with Y-axis labels
    println(s"\n${BOLD}Cartesian Plane  (Y↑  X→)${RST}")
    val planeRows = planeColor.split('\n')
    val yLabels   = Array.fill(Plane.GH)("   ")
    for wy <- 0 to 100 by 10 do
      val row = Plane.gy(wy)
      if row >= 0 && row < Plane.GH then yLabels(row) = f"$wy%3d"
    for (row, i) <- planeRows.zipWithIndex do
      val lbl = if i < yLabels.length then yLabels(i) else "   "
      println(s"$lbl $row")

    // Tree
    println(s"\n${BOLD}K-D Tree so far${RST}  " +
            s"(${RED}X${RST}=vertical  ${BLU}Y${RST}=horizontal)")
    treeLns.foreach(println)

    // Save
    saveStep(outDir, stepNum, bs, planePlain, sortPlain, treePlain)
    logPw.println(s"\nStep $stepNum – Node ${med.name}(${med.x},${med.y}) depth=${bs.depth} dim=${if bs.dim==0 then "X" else "Y"}")
    logPw.println(s"  Input:  ${bs.subset.map(p => s"${p.name}(${p.x},${p.y})").mkString(" ")}")
    logPw.println(s"  Sorted: ${bs.sorted.map(p => s"${p.name}(${if bs.dim==0 then p.x else p.y})").mkString(" ")}")
    logPw.println(s"  Left:   ${bs.sorted.take(bs.medIdx).map(_.name).mkString(" ")}")
    logPw.println(s"  Right:  ${bs.sorted.drop(bs.medIdx+1).map(_.name).mkString(" ")}")
    treePlain.foreach(l => logPw.println("  " + l))

    println(s"\n${WHT}Saved → $outDir/step_${"%02d".format(stepNum)}.txt${RST}")
    if stepIdx < steps.size - 1 then
      println(s"${WHT}Press SPACE for next step…${RST}")
      waitForSpace()

  // ── final ────────────────────────────────────────────────────
  logPw.println("\n" + "=" * 60)
  logPw.println("Build complete.")
  logPw.close()

  println()
  println(s"${BOLD}${GRN}✓ Tree complete! All steps saved to ./$outDir/${RST}")
  println(s"\nFiles: $outDir/build_log.txt  +  step_01…step_${"%02d".format(steps.size)}.txt")
