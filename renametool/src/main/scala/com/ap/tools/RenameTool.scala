package com.ap.tools

/**
 * Created by ptanapaisankit on 5/22/2014.
 */

import brut.androlib.{ApkDecoder, Androlib}
import java.io.{IOException, File}
import java.lang.reflect.Array
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._
import java.util.concurrent.TimeUnit
import org.apache.commons.cli._
import scala.util.Random

object RenameTool {

  def createOptions():Options = {
    val iOpt = OptionBuilder.isRequired.hasArg(true).create("i")
    val oOpt = OptionBuilder.hasArg(true).create("o")
    val pOpt = OptionBuilder.isRequired.hasArg(true).create("p")
    val vOpt = OptionBuilder.hasArg(true).create("v")
    val options = new Options
    options.addOption(iOpt)
    options.addOption(oOpt)
    options.addOption(pOpt)
    options.addOption(vOpt)
  }

  def main(args: Array[String]) {
    // cli parser
    val parser = new PosixParser
    val options = createOptions()
    val commandLine: CommandLine = try {
      parser.parse(options, args, false)
    } catch {
      case e: Exception => println(e.toString)
    }

    val inputApk = commandLine.getOptionValue("i")
    val outputPackageName = commandLine.getOptionValue("p")

    println("target apk : " + inputApk)
    println("new package name : " + outputPackageName)

    //    val inputApk = "TMobile.apk"
//    val outputPackageName = "com.ap.tmo"

//    if(args.length != 2) {
//      println("please specify the target apk and the new package name.")
//      println("eg. java -jar renametool.jar original.apk com.ap.newname")
//      return
//    }

//    val inputApk = args(0)
//    val outputPackageName = args(1)

//    println("target apk : " + args(0))
//    println("new package name : " + args(1))

    val outputApk = "mod" + inputApk
    //val inputPackageName = "com.aetherpal.tmrs"

    val ourDir = new File(Random.nextLong.toString)

    try {
      val decoder = new ApkDecoder

      decoder.setOutDir(ourDir)
      decoder.setApkFile(new File(inputApk))
      decoder.setForceDelete(true)
      decoder.decode

      val out = new java.io.BufferedWriter(new java.io.FileWriter(ourDir.getAbsolutePath + File.separator + "newManifest.xml"))
      val androidManifestXml = ourDir.getAbsolutePath + File.separator + "AndroidManifest.xml"
      val newManifestXml = ourDir.getAbsolutePath + File.separator + "newManifest.xml"
      val manifest = io.Source.fromFile(androidManifestXml)
      val lines = manifest.getLines

      var changed = false
      lines.foreach(line => {
        if (line.contains("package=") && !changed) {
          val begin = line.indexOf("package=\"") + "package=\"".length
          val end = line.indexOf("\"", begin)
          val old = line.substring(begin, end)
          out.write(line.replace("package=\"" + old + "\"", "package=\"" + outputPackageName + "\""))
          changed = true
        } else {
          out.write(line)
        }
      })
      out.close
      manifest.close

      val oldManifest = new File(androidManifestXml)
      if (oldManifest.exists() && oldManifest.delete())
        println("deleted the old manifest.")

      val newManifest = new File(newManifestXml)
      newManifest.renameTo(new File(androidManifestXml))

      val aLib = new Androlib
      aLib.installFramework(new File("framework-res.apk"), null, null)
      val flags: java.util.HashMap[String, java.lang.Boolean] = new java.util.HashMap[String, java.lang.Boolean]
      flags.put("forceBuildAll", false)
      flags.put("debug", false)
      flags.put("verbose", false)
      flags.put("framework", false)
      flags.put("update", false)
      flags.put("copyOriginal", false)

      aLib.build(ourDir, new File(outputApk), flags, "")

      delete(ourDir)

    } catch {
      case ex: Exception => println("Bummer, " + ex.toString)
    }

    //aLib.build doesn't close streams properly...
    //so we have to do our own clean-up.
    System.gc
    val stop = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (ourDir.exists() && stop > System.nanoTime())
      try {
        delete(FileSystems.getDefault().getPath(ourDir.getAbsolutePath))
      } catch {
        case ex: Exception => println("bummer, please wait. cleaning up...")
      } finally System.gc()

    println("tada...new apk is " + outputApk + '!')
  }

  def delete(path: Path) {
    if (Files.exists(path)) Files.walkFileTree(path, new SimpleFileVisitor[Path]() {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.deleteIfExists(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
        if (exc == null) {
          Files.deleteIfExists(dir)
          FileVisitResult.CONTINUE
        } else {
          throw exc
        }
      }
    })
  }

  /** Deletes all empty directories in the set.  Any non-empty directories are ignored. */
  def deleteIfEmpty(dirs: collection.Set[File]): Unit = {
    val isEmpty = new scala.collection.mutable.HashMap[File, Boolean]
    def visit(f: File): Boolean = isEmpty.getOrElseUpdate(f, dirs(f) && f.isDirectory && (f.listFiles forall visit))

    dirs foreach visit
    for ((f, true) <- isEmpty) f.delete
  }

  /** Deletes each file or directory (recursively) in `files`. */
  def delete(files: Iterable[File]): Unit = files.foreach(delete)

  /** Deletes `file`, recursively if it is a directory. */
  def delete(file: File) {
    val deleted = file.delete
    if (!deleted && file.isDirectory) {
      delete(listFiles(file))
      file.delete
    }
  }

  /** Returns the children of directory `dir` that match `filter` in a non-null array. */
  def listFiles(filter: java.io.FileFilter)(dir: File): Array[File] = wrapNull(dir.listFiles(filter))

  /** Returns the children of directory `dir` that match `filter` in a non-null array. */
  def listFiles(dir: File, filter: java.io.FileFilter): Array[File] = wrapNull(dir.listFiles(filter))

  /** Returns the children of directory `dir` in a non-null array. */
  def listFiles(dir: File): Array[File] = wrapNull(dir.listFiles())

  def wrapNull(a: Array[File]) =
    if (a == null)
      new Array[File](0)
    else
      a
}
