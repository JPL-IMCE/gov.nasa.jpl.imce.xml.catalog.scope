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
import scala.Predef.classOf

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

  "bad catalog" should "fail" in withScopedCatalog { (_, cat) =>
    val xml = new URL("file:/does/not/exist")
    assertThrows[IOException](cat.parseCatalog(xml))
  }

  "vocabularies1/oml.catalog.xml scope" should "be ok" in withScopedCatalog {
    (_, cat) =>
      cat.parseCatalog(
        classOf[CatalogScopeTest].getResource("/vocabularies1/oml.catalog.xml"))

      val scope: Map[String, Seq[Path]] =
        cat.localFileScope(new CatalogEntryFilePredicate {
          override def apply(uriStartString: String, path: Path): Boolean = {
            assert(uriStartString.startsWith("http://"))
            path.toIO.exists && path.isFile && path.last.endsWith(".oml")
          }

          override def expandLocalFilePath(pathPrefix: Path): Set[Path] = {
            val expanded: Path = pathPrefix / up / (pathPrefix.last + ".oml")
            Set(expanded)
          }
        })

      // verify that there are exactly 5 rewrite rules and correspondingly 5 sets of files.
      assert(5 == cat.entries().count(_.getEntryType == Catalog.REWRITE_URI))
      assert(5 == scope.size)

      // verify that the scope for each rewrite uri rule is exactly 1 file
      scope.values.foreach { files =>
        assert(1 == files.size)
        files.foreach { f =>
          assert(f.toIO.exists)
          assert(f.isFile)
        }
      }

      val files: Set[Path] = scope.values.flatMap(_.to[Set]).to[Set]
      Set[String](
        "http://www.w3.org/2002/07/owl",
        "http://purl.org/dc/elements/1.1/",
        "http://imce.jpl.nasa.gov/foundation/annotation/annotation",
        "http://imce.jpl.nasa.gov/foundation/base/base",
        "http://imce.jpl.nasa.gov/foundation/base/base-embedding"
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

      val scope: Map[String, Seq[Path]] =
        cat.localFileScope(new CatalogEntryFilePredicate {
          override def apply(uriStartString: String, path: Path): Boolean = {
            assert(uriStartString.startsWith("http://"))
            path.toIO.exists && path.isFile && path.last.endsWith(".oml")
          }

          override def expandLocalFilePath(pathPrefix: Path): Set[Path] = {
            val expanded: Path = pathPrefix / up / (pathPrefix.last + ".oml")
            Set(expanded)
          }
        })

      // verify that there are exactly 7 rewrite rules but only 5 sets of files.
      assert(8 == cat.entries().count(_.getEntryType == Catalog.REWRITE_URI))
      assert(5 == scope.size)

      // verify that the scope for each rewrite uri rule is exactly 1 file
      scope.values.foreach { files =>
        assert(1 == files.size)
        files.foreach { f =>
          assert(f.toIO.exists)
          assert(f.isFile)
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
}