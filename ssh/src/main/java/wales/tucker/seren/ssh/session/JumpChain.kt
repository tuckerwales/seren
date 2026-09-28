package wales.tucker.seren.ssh.session

/**
 * Orders jump hosts for a ProxyJump-style chain: walk [jumpOf] from [startId], stop on cycles or
 * missing nodes, then reverse so the outermost hop is first.
 */
fun <T> resolveJumpHosts(startId: T, jumpOf: (T) -> T?): List<T> {
    val hops = mutableListOf<T>()
    val visited = mutableSetOf(startId)
    var jumpId = jumpOf(startId)
    while (jumpId != null) {
        if (!visited.add(jumpId)) break
        hops.add(jumpId)
        jumpId = jumpOf(jumpId)
    }
    return hops.asReversed()
}
