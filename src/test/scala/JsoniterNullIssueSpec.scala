import java.io.{BufferedInputStream, File, FileOutputStream}
import java.nio.file.Paths
import java.util
import java.util.concurrent.{ExecutorService, Executors}

import cats.effect.{Blocker, ContextShift, IO, Resource}
import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.{
  CodecMakerConfig,
  JsonCodecMaker
}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import fs2.Stream
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.fs2.{byteStreamParser, decoder => circeDecoder}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext

final case class FooWithNoneMiddle(name: String,
                                   empty: Option[Boolean] = None,
                                   num: Int)
final case class FooWithNoneLast(name: String,
                                   num: Int,
                                   empty: Option[Boolean] = None)
final case class Foo(name: String, num: Int)

class JsoniterNullIssueSpec
    extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {
  behavior of "jsoniter"

  private val chunkSize = 100

  val tmpFilenameWithoutNulls = "/tmp/fred-and-wilma.json"
  val tmpFilenameWithNullMiddle = "/tmp/fred-and-wilma-null-middle.json"
  val tmpFilenameWithNullLast = "/tmp/fred-and-wilma-null-last.json"

  val cs1ThreadPool: ExecutorService =
    Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors() * 2)
  implicit val cs1: ContextShift[IO] =
    IO.contextShift(ExecutionContext.fromExecutor(cs1ThreadPool))
  val blockerR: Resource[IO, Blocker] =
    cats.effect.Blocker[IO](cats.effect.IO.ioConcurrentEffect(cs1))

  val codecFooWithNulls: JsonValueCodec[FooWithNoneMiddle] =
    JsonCodecMaker.make[FooWithNoneMiddle](
      CodecMakerConfig
        .withTransientNone(false)
        .withTransientEmpty(false)
        .withTransientDefault(false)
    )
  implicit val codecFooWithNullLast: JsonValueCodec[FooWithNoneLast] =
    JsonCodecMaker.make[FooWithNoneLast](
      CodecMakerConfig
        .withTransientNone(false)
        .withTransientEmpty(false)
        .withTransientDefault(false)
    )
  implicit val codecFooWithoutNulls: JsonValueCodec[FooWithNoneMiddle] =
    JsonCodecMaker.make[FooWithNoneMiddle](CodecMakerConfig)
  implicit val codecFoo: JsonValueCodec[Foo] =
    JsonCodecMaker.make[Foo](CodecMakerConfig)

  implicit val codecFooCirce: Decoder[Foo] = deriveDecoder[Foo]

  val fooWithNoneMiddles: List[FooWithNoneMiddle] = List
    .fill(200)(
      List(
        FooWithNoneMiddle(name = "fred", num = 1),
        FooWithNoneMiddle(name = "wilma", num = 2)
      )
    )
    .flatten
  val foos: List[Foo] =
    fooWithNoneMiddles.map(fooWithNoneMiddle => Foo(fooWithNoneMiddle.name, fooWithNoneMiddle.num))

  override def beforeAll(): Unit = {
    val outStreamWithoutNulls = new FileOutputStream(
      new File(tmpFilenameWithoutNulls)
    )
    val outStreamWithNulls = new FileOutputStream(
      new File(tmpFilenameWithNullMiddle)
    )
    val outStreamWithNullLast = new FileOutputStream(
      new File(tmpFilenameWithNullLast)
    )
    try {
      outStreamWithNulls.getChannel.truncate(0)
      outStreamWithoutNulls.getChannel.truncate(0)
      outStreamWithNullLast.getChannel.truncate(0)
      fooWithNoneMiddles.foreach { fooWithNone =>
        writeToStream(fooWithNone, outStreamWithoutNulls)(codecFooWithoutNulls)
        outStreamWithoutNulls.write("\n".getBytes)
        writeToStream(fooWithNone, outStreamWithNulls)(codecFooWithNulls)
        outStreamWithNulls.write("\n".getBytes)
        writeToStream(FooWithNoneLast(fooWithNone.name, fooWithNone.num, fooWithNone.empty), outStreamWithNullLast)
        outStreamWithNullLast.write("\n".getBytes)
      }
    } finally {
      outStreamWithNulls.close()
      outStreamWithoutNulls.close()
      outStreamWithNullLast.close()
    }
  }

  it should "succeed when reading a FooWithNoneMiddle stream from tmpFilenameWithoutNulls" in {
    val foosRead = new util.ArrayDeque[FooWithNoneMiddle]
    val readThem = blockerR.use { blocker =>
      fs2.io.file
        .readAll[IO](Paths.get(tmpFilenameWithoutNulls), blocker, chunkSize)
        .through(fs2.io.toInputStream[IO])
        .flatMap { in =>
          Stream.eval(
            cs1.evalOn(ExecutionContext.fromExecutor(cs1ThreadPool)) {
              IO(
                scanJsonValuesFromStream[FooWithNoneMiddle](
                  new BufferedInputStream(in)
                ) { fooWithNone =>
                  foosRead.add(fooWithNone)
                  true
                }
              )
            }
          )
        }
        .compile
        .drain
    }
    readThem.unsafeRunSync()
    foosRead.asScala.toList should contain theSameElementsInOrderAs fooWithNoneMiddles
  }

  it should "succeed when reading a FooWithNoneMiddle stream from tmpFilenameWithNullLast" in {
    val foosRead = new util.ArrayDeque[FooWithNoneMiddle]
    val readThem = blockerR.use { blocker =>
      fs2.io.file
        .readAll[IO](Paths.get(tmpFilenameWithNullLast), blocker, chunkSize)
        .through(fs2.io.toInputStream[IO])
        .flatMap { in =>
          Stream.eval(
            cs1.evalOn(ExecutionContext.fromExecutor(cs1ThreadPool)) {
              IO(
                scanJsonValuesFromStream[FooWithNoneMiddle](
                  new BufferedInputStream(in)
                ) { fooWithNone =>
                  foosRead.add(fooWithNone)
                  true
                }
              )
            }
          )
        }
        .compile
        .drain
    }
    readThem.unsafeRunSync()
    foosRead.asScala.toList should contain theSameElementsInOrderAs fooWithNoneMiddles
  }

  it should "succeed when reading a FooWithNoneMiddle stream from tmpFilenameWithNullMiddle" in {
    val foosRead = new util.ArrayDeque[FooWithNoneMiddle]
    val readThem = blockerR.use { blocker =>
      fs2.io.file
        .readAll[IO](Paths.get(tmpFilenameWithNullMiddle), blocker, chunkSize)
        .through(fs2.io.toInputStream[IO])
        .flatMap { in =>
          Stream.eval(
            cs1.evalOn(ExecutionContext.fromExecutor(cs1ThreadPool)) {
              IO(
                scanJsonValuesFromStream[FooWithNoneMiddle](
                  new BufferedInputStream(in)
                ) { fooWithNone =>
                  foosRead.add(fooWithNone)
                  true
                }
              )
            }
          )
        }
        .compile
        .drain
    }
    readThem.unsafeRunSync()
    foosRead.asScala.toList should contain theSameElementsInOrderAs fooWithNoneMiddles
  }

  it should "succeed when reading a Foo stream from tmpFilenameWithoutNulls" in {
    val foosRead = new util.ArrayDeque[Foo]
    val readThem = blockerR.use { blocker =>
      fs2.io.file
        .readAll[IO](Paths.get(tmpFilenameWithoutNulls), blocker, chunkSize)
        .through(fs2.io.toInputStream[IO])
        .flatMap { in =>
          Stream.eval(cs1.evalOn(ExecutionContext.fromExecutor(cs1ThreadPool)) {
            IO(scanJsonValuesFromStream[Foo](new BufferedInputStream(in)) {
              foo =>
                foosRead.add(foo)
                true
            })
          })
        }
        .compile
        .drain
    }
    readThem.unsafeRunSync()
    foosRead.asScala.toList should contain theSameElementsInOrderAs foos
  }

  it should "not fail when reading a Foo stream from tmpFilenameWithNulls (but it does)" in {
    val foosRead = new util.ArrayDeque[Foo]
    val readThem = blockerR.use { blocker =>
      fs2.io.file
        .readAll[IO](Paths.get(tmpFilenameWithNullMiddle), blocker, chunkSize)
        .through(fs2.io.toInputStream[IO])
        .flatMap { in =>
          Stream.eval(cs1.evalOn(ExecutionContext.fromExecutor(cs1ThreadPool)) {
            IO(scanJsonValuesFromStream[Foo](new BufferedInputStream(in)) {
              foo =>
                foosRead.add(foo)
                true
            })
          })
        }
        .compile
        .drain
    }
    readThem.unsafeRunSync()
    foosRead.asScala.toList should contain theSameElementsInOrderAs foos
  }

  it should "succeed when reading a Foo stream from tmpFilenameWithNullLast" in {
    val foosRead = new util.ArrayDeque[Foo]
    val readThem = blockerR.use { blocker =>
      fs2.io.file
        .readAll[IO](Paths.get(tmpFilenameWithNullLast), blocker, chunkSize)
        .through(fs2.io.toInputStream[IO])
        .flatMap { in =>
          Stream.eval(cs1.evalOn(ExecutionContext.fromExecutor(cs1ThreadPool)) {
            IO(scanJsonValuesFromStream[Foo](new BufferedInputStream(in)) {
              foo =>
                foosRead.add(foo)
                true
            })
          })
        }
        .compile
        .drain
    }
    readThem.unsafeRunSync()
    foosRead.asScala.toList should contain theSameElementsInOrderAs foos
  }

  it should "succeed when reading a Foo stream from tmpFilenameWithNulls using Circe" in {
    val readThem = blockerR.use { blocker =>
      fs2.io.file
        .readAll[IO](Paths.get(tmpFilenameWithNullMiddle), blocker, chunkSize)
        .through(byteStreamParser[IO])
        .through(circeDecoder[IO, Foo])
        .compile
        .toList
    }
    readThem.unsafeRunSync() should contain theSameElementsInOrderAs foos
  }

  it should "succeed when reading a Foo stream from tmpFilenameWithNullLast using Circe" in {
    val readThem = blockerR.use { blocker =>
      fs2.io.file
        .readAll[IO](Paths.get(tmpFilenameWithNullLast), blocker, chunkSize)
        .through(byteStreamParser[IO])
        .through(circeDecoder[IO, Foo])
        .compile
        .toList
    }
    readThem.unsafeRunSync() should contain theSameElementsInOrderAs foos
  }
}
