package io.iohk.ethereum.blockchain.sync

import akka.event.LoggingAdapter
import akka.util.ByteString
import io.iohk.ethereum.blockchain.sync.BlacklistSupport.BlackListId
import io.iohk.ethereum.blockchain.sync.FastSyncBlocksValidator.BlockBodyValidationResult
import io.iohk.ethereum.network.Peer
import io.iohk.ethereum.network.p2p.messages.PV62.BlockBody
import io.iohk.ethereum.utils.Config.SyncConfig
import org.bouncycastle.util.encoders.Hex

import scala.concurrent.duration.FiniteDuration

trait FastSyncBlockBodiesHandler extends FastSyncBlocksValidator {

  def syncConfig: SyncConfig
  def log: LoggingAdapter

  def handleBlockBodies(
    peer: Peer,
    requestedHashes: Seq[ByteString],
    blockBodies: Seq[BlockBody],
    handlerState: FastSyncHandlerState,
    blacklist: (BlackListId, FiniteDuration, String) => Unit,
    updateBestBlock: (Seq[ByteString]) => Unit
  ): FastSyncHandlerState = {
    if (blockBodies.isEmpty) {
      val hashes = requestedHashes.map(h => Hex.toHexString(h.toArray[Byte]))
      val reason = s"got empty block bodies response for known hashes: $hashes"
      blacklist(peer.id, syncConfig.blacklistDuration, reason)
      handlerState.withEnqueueBlockBodies(requestedHashes)
    } else {
      validateBlocks(requestedHashes, blockBodies) match {
        case BlockBodyValidationResult.Valid   =>
          insertBlocks(requestedHashes, blockBodies, handlerState, updateBestBlock)

        case BlockBodyValidationResult.Invalid =>
          val reason = s"responded with block bodies not matching block headers, blacklisting for ${syncConfig.blacklistDuration}"
          blacklist(peer.id, syncConfig.blacklistDuration, reason)
          handlerState.withEnqueueBlockBodies(requestedHashes)

        case BlockBodyValidationResult.DbError =>
          log.debug("Missing block header for known hash")
          handlerState.reduceQueuesAndBestBlock(syncConfig.blockHeadersPerRequest)
      }
    }
  }

  private def insertBlocks(
    requestedHashes: Seq[ByteString],
    blockBodies: Seq[BlockBody],
    handlerState: FastSyncHandlerState,
    updateBestBlock: (Seq[ByteString]) => Unit
  ): FastSyncHandlerState = {
    for ((hash, body) <- requestedHashes zip blockBodies) yield blockchain.save(hash, body)

    val (toUpdate, remaining) = requestedHashes.splitAt(blockBodies.size)
    updateBestBlock(toUpdate)
    if (remaining.nonEmpty) {
      handlerState.withEnqueueBlockBodies(remaining)
    } else {
      handlerState
    }
  }
}