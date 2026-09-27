package wales.tucker.seren.ssh.ssh

import com.jcraft.jsch.JSch

/** Forces the Bouncy Castle implementations JSch selects on Android, so JVM tests exercise them. */
object JschAndroidConfig {
    fun apply() {
        JSch.setConfig("xdh", "com.jcraft.jsch.bc.XDH")
        JSch.setConfig("keypairgen.eddsa", "com.jcraft.jsch.bc.KeyPairGenEdDSA")
        JSch.setConfig("ssh-ed25519", "com.jcraft.jsch.bc.SignatureEd25519")
        JSch.setConfig("ssh-ed448", "com.jcraft.jsch.bc.SignatureEd448")
        JSch.setConfig("mlkem768", "com.jcraft.jsch.bc.MLKEM768")
        JSch.setConfig("mlkem1024", "com.jcraft.jsch.bc.MLKEM1024")
    }
}
