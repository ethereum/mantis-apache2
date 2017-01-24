package io.iohk.ethereum

import java.math.BigInteger
import org.scalacheck.{Arbitrary, Gen}

trait ObjectGenerators {

  lazy val intGen: Gen[Int] = Gen.choose(Int.MinValue, Int.MaxValue)

  lazy val bigIntGen: Gen[BigInt] = byteArrayOfNItemsGen(32).map(b=>new BigInteger(1, b))

  lazy val anyArrayGen: Gen[Array[Byte]] = Gen.nonEmptyListOf(Arbitrary.arbitrary[Byte]).map(_.toArray)

  def byteArrayOfNItemsGen(n: Int): Gen[Array[Byte]] = Gen.listOfN(n, Arbitrary.arbitrary[Byte]).map(_.toArray)

  def seqByteArrayOfNItemsGen(n: Int): Gen[Seq[Array[Byte]]] = Gen.listOf(byteArrayOfNItemsGen(n))

  def hexPrefixDecodeParametersGen(): Gen[(Array[Byte], Boolean)] = for {
    aByteList <- Gen.nonEmptyListOf(Arbitrary.arbitrary[Byte])
    t <- Arbitrary.arbitrary[Boolean]
  } yield (aByteList.toArray, t)

  def keyValueListGen(): Gen[List[(Int, Int)]] = for {
    aKeyList <- Gen.nonEmptyListOf(Arbitrary.arbitrary[Int]).map(_.distinct)
  } yield aKeyList.zip(aKeyList)

}