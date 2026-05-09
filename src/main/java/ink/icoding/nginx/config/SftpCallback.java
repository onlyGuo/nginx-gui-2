package ink.icoding.nginx.config;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

@FunctionalInterface
public interface SftpCallback<T> {
    T doWithChannel(ChannelSftp channel) throws SftpException, JSchException;
}
