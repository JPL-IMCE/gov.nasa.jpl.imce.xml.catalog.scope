package test

import java.io.IOException
import java.net.URL

import ammonite.ops.{Path, up}
import gov.nasa.jpl.imce.xml.catalog.scope.{
  CatalogEntryFilePredicate,
  CatalogScope,
  CatalogScopeManager
}
import org.apache.xml.resolver.Catalog
import org.scalatest._

import scala.collection.immutable.{Map, Seq, Set}
import scala.{StringContext, Unit}
import scala.Predef.{augmentString,classOf,ArrowAssoc}

class CatalogScopeTest extends FlatSpec {

  def withScopedCatalog(
      testCode: (CatalogScopeManager, CatalogScope) => Unit): Unit = {
    val csm: CatalogScopeManager = new CatalogScopeManager()
    val cat: CatalogScope = csm.getCatalog
    testCode(csm, cat)
  }

  "CatalogScopeManager.getCatalog" should
    "return a different catalog than CatalogScopeManager.getPrivateCatalog" in
    withScopedCatalog { (csm, cat) =>
      assert(cat !== csm.getPrivateCatalog)
    }

  "multiple calls to CatalogScopeManager.getCatalog" should
    "return the different catalogs" in withScopedCatalog { (csm, cat) =>
    val c1 = csm.getCatalog
    val c2 = csm.getCatalog
    assert(c1 !== cat)
    assert(c2 !== cat)
    assert(c1 !== c2)
  }

  "after creation, catalog" should "be empty" in withScopedCatalog { (csm, _) =>
    assert(csm.getCatalog.getParsedCatalogs().isEmpty)
  }

  "load oml.catalog.xml" should "be ok" in withScopedCatalog { (_, cat) =>
    val oml_catalog_xml =
      classOf[CatalogScopeTest].getResource("/vocabularies1/oml.catalog.xml")
    assert(null != oml_catalog_xml)
    cat.parseCatalog(oml_catalog_xml)
    assert(cat.getParsedCatalogs().size == 1)
    assert(cat.entries().nonEmpty)
  }

  "bad catalog url" should "fail" in withScopedCatalog { (_, cat) =>
    val xml = new URL("file:/does/not/exist")
    assertThrows[IOException](cat.parseCatalog(xml))
  }

  "bad catalog file" should "fail silently" in withScopedCatalog { (_, cat) =>
    val xml = classOf[CatalogScopeTest].getResource("/badCatalog/catalog.xml")
    cat.parseCatalog(xml)
    assert(cat.getParsedCatalogs().size == 1)
    assert(cat.entries().count(_.getEntryType == Catalog.REWRITE_URI) == 1)


    val scope: Map[Path, (Path, String)] =
      cat.localFileScope(new CatalogEntryFilePredicate {
        override def apply(uriStartString: String, path: Path): Boolean = {
          assert(uriStartString.startsWith("http://"))
          path.toIO.exists && path.isFile && path.last.endsWith(".oml")
        }

        override def expandLocalFilePath(pathPrefix: Path): Set[Path] = {
          val expanded: Path = pathPrefix / up / (pathPrefix.last + ".oml")
          Set(expanded)
        }

        override val fileExtensions: Set[String] = Set(".oml")
      })
    assert(scope.size == 0)

    Set[String](
      "http://www.w3.org/2002/07/owl"
    ).foreach { uri =>
      val resolved = cat.resolveURIWithExtension(uri, ".oml")
      assert(resolved.isEmpty)
    }

  }

  "vocabularies1/oml.catalog.xml scope" should "be ok" in withScopedCatalog {
    (_, cat) =>
      cat.parseCatalog(
        classOf[CatalogScopeTest].getResource("/vocabularies1/oml.catalog.xml"))

      val predicate = new CatalogEntryFilePredicate {
        override def apply(uriStartString: String, path: Path): Boolean = {
          assert(uriStartString.startsWith("http://"))
          path.toIO.exists && path.isFile && path.last.endsWith(".oml")
        }

        override def expandLocalFilePath(pathPrefix: Path): Set[Path] = {
          val expanded: Path = pathPrefix / up / (pathPrefix.last + ".oml")
          Set(expanded)
        }

        override val fileExtensions: Set[String] = Set(".oml")
      }

      val scope: Map[Path, (Path, String)] = cat.localFileScope(predicate)

      // verify that there are exactly 6 rewrite rules and correspondingly 6 sets of files.
      assert(cat.entries().count(_.getEntryType == Catalog.REWRITE_URI) == 6)
      assert(scope.size == 6)

      val csFiles: Map[String, Path] = cat.iri2file(scope, predicate)
      assert(csFiles.size == 6)
      csFiles.foreach { case (uri, file) =>
        assert(file.toIO.exists, uri)
      }

      val converted = csFiles.map { case (uri, path) =>
        predicate.fileExtensions.foldLeft(uri)(_.stripSuffix(_)) -> path
      }
      assert(converted == csFiles)

      val files: Set[Path] = scope.keys.to[Set]
      Set[String](
        "http://www.w3.org/2002/07/owl",
        "http://purl.org/dc/elements/1.1/",
        "http://imce.jpl.nasa.gov/foundation/annotation/annotation",
        "http://imce.jpl.nasa.gov/foundation/base/base",
        "http://imce.jpl.nasa.gov/foundation/base/base-embedding",
        "http://imce.jpl.nasa.gov/foundation/fact/fact"
      ).foreach { uri =>
        val resolved = cat.resolveURIWithExtension(uri, ".oml")
        assert(resolved.isDefined)
        resolved.map(r => assert(files.contains(r)))
      }

      Set[String](
        "http://does/not/exist",
        "http://www.w3.org/2002/owl",
        "http://imce.jpl.nasa.gov/foundation/annotation/shouldNotBeIncluded",
        "http://imce.jpl.nasa.gov/foundation/base/shouldNotBeIncluded.oml",
        "http://imce.jpl.nasa.gov/foundation/base/base.oml"
      ).foreach { uri =>
        cat.resolveURIWithExtension(uri, ".oml") match {
          case None =>
            ()
          case Some(resolved) =>
            assert(!resolved.toIO.exists,
                   s"$uri should not have resolved to: $resolved")
        }
      }
  }

  "vocabularies2/oml.catalog.xml scope" should "be ok" in withScopedCatalog {
    (_, cat) =>
      cat.parseCatalog(
        classOf[CatalogScopeTest].getResource("/vocabularies2/oml.catalog.xml"))

      val scope: Map[Path, (Path, String)] =
        cat.localFileScope(new CatalogEntryFilePredicate {
          override def apply(uriStartString: String, path: Path): Boolean = {
            assert(uriStartString.startsWith("http://"))
            path.toIO.exists && path.isFile && path.last.endsWith(".oml")
          }

          override def expandLocalFilePath(pathPrefix: Path): Set[Path] = {
            val expanded: Path = pathPrefix / up / (pathPrefix.last + ".oml")
            Set(expanded)
          }

          override val fileExtensions: Set[String] = Set(".oml")
        })

      // verify that there are exactly 7 rewrite rules but only 5 sets of files.
      assert(8 == cat.entries().count(_.getEntryType == Catalog.REWRITE_URI))
      assert(5 == scope.size)
  }

  "vocabularies3/oml.catalog.xml scope" should "be ok" in withScopedCatalog {
    (_, cat) =>
      cat.parseCatalog(
        classOf[CatalogScopeTest].getResource("/vocabularies3/oml.catalog.xml"))

      val predicate = new CatalogEntryFilePredicate {
        override def apply(uriStartString: String, path: Path): Boolean = {
          assert(uriStartString.startsWith("http://"))
          path.toIO.exists && path.isFile && (path.last.endsWith(".oml") || path.last.endsWith(".omlzip"))
        }

        override def expandLocalFilePath(pathPrefix: Path): Set[Path] = {
          val expanded1: Path = pathPrefix / up / (pathPrefix.last + ".oml")
          val expanded2: Path = pathPrefix / up / (pathPrefix.last + ".omlzip")
          Set(expanded1,expanded2)
        }

        override val fileExtensions: Set[String] = Set(".oml", ".omlzip")
      }

      val scope: Map[Path, (Path, String)] = cat.localFileScope(predicate)

      // verify that there are exactly 6 rewrite rules and correspondingly 6 sets of files.
      assert(cat.entries().count(_.getEntryType == Catalog.REWRITE_URI) == 6)
      assert(scope.size == 6)

      val csFiles: Map[String, Path] = cat.iri2file(scope, predicate)


      val converted = csFiles.map { case (uri, path) =>
        predicate.fileExtensions.foldLeft(uri)(_.stripSuffix(_)) -> path
      }
      assert(converted == csFiles)

      val files: Set[Path] = scope.keys.to[Set]
      Set[String](
        "http://www.w3.org/2002/07/owl",
        "http://purl.org/dc/elements/1.1/",
        "http://imce.jpl.nasa.gov/foundation/annotation/annotation",
        "http://imce.jpl.nasa.gov/foundation/base/base",
        "http://imce.jpl.nasa.gov/foundation/base/base-embedding",
        "http://imce.jpl.nasa.gov/foundation/fact/fact"
      ).foreach { uri =>
        val resolved1 = cat.resolveURIWithExtension(uri, ".oml").flatMap { f =>
          if (f.toIO.exists()) Some(f) else None
        }
        val resolved2 = cat.resolveURIWithExtension(uri, ".omlzip").flatMap { f =>
          if (f.toIO.exists()) Some(f) else None
        }
        assert((resolved1.isDefined && resolved2.isEmpty) || (resolved1.isEmpty && resolved2.isDefined))
        resolved1.map(r => assert(files.contains(r)))
        resolved2.map(r => assert(files.contains(r)))
      }

      Set[String](
        "http://does/not/exist",
        "http://www.w3.org/2002/owl",
        "http://imce.jpl.nasa.gov/foundation/annotation/shouldNotBeIncluded",
        "http://imce.jpl.nasa.gov/foundation/base/shouldNotBeIncluded.oml",
        "http://imce.jpl.nasa.gov/foundation/base/base.oml"
      ).foreach { uri =>
        cat.resolveURIWithExtension(uri, ".oml") match {
          case None =>
            ()
          case Some(resolved) =>
            assert(!resolved.toIO.exists,
              s"$uri should not have resolved to: $resolved")
        }
      }
  }

  "bogus URI with matching prefixes" should "resolve to non-existent files" in withScopedCatalog {
    (_, cat) =>
      cat.parseCatalog(
        classOf[CatalogScopeTest].getResource("/vocabularies2/oml.catalog.xml"))

      // These URIs should resolve to non-existent files.
      Set[String]("http://www.w3.org/2002/owl",
                  "http://imce.jpl.nasa.gov/foundation/base/base.oml").foreach {
        uri =>
          cat.resolveURIWithExtension(uri, ".oml") match {
            case None =>
              fail(uri)
            case Some(resolved) =>
              assert(!resolved.toIO.exists,
                     s"$uri should not have resolved to: $resolved")
          }
      }
  }

  "URI with non-matching prefixes" should "not resolve at all" in withScopedCatalog {
    (_, cat) =>
      Set[String](
        "http://imce.jpl.nasa.gov/foundation/annotation/shouldNotBeIncluded",
        "http://imce.jpl.nasa.gov/foundation/base/shouldNotBeIncluded.oml",
        "http://does/not/exist",
        "http://strange/weird"
      ).foreach { uri =>
        cat.resolveURIWithExtension(uri, ".oml") match {
          case None =>
            ()
          case Some(resolved) =>
            fail(s"$uri should not have resolved to: $resolved")
        }
      }
  }

  "vocabularies 4/oml.catalog.xml scope" should "be ok" in withScopedCatalog {
    (_, cat) =>
      cat.parseCatalog(classOf[CatalogScopeTest].getResource("/vocabularies 4/oml.catalog.xml"))

      val scope: Map[Path, (Path, String)] =
        cat.localFileScope(new CatalogEntryFilePredicate {
          override def apply(uriStartString: String, path: Path): Boolean = {
            assert(uriStartString.startsWith("http://"))
            path.toIO.exists && path.isFile && path.last.endsWith(".oml")
          }

          override def expandLocalFilePath(pathPrefix: Path): Set[Path] = {
            val expanded: Path = pathPrefix / up / (pathPrefix.last + ".oml")
            Set(expanded)
          }

          override val fileExtensions: Set[String] = Set(".oml")
        })

      // verify that there are exactly 6 rewrite rules but only 5 sets of files.
      assert(6 == cat.entries().count(_.getEntryType == Catalog.REWRITE_URI))
      assert(5 == scope.size)

      scope.foreach { case (_, (_, uriPrefix)) =>
        assert(uriPrefix.length > "http://".length)
      }

  }
}
