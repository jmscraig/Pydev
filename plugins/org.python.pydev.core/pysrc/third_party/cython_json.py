import Cython
from Cython.Compiler import Nodes
from Cython.Compiler.Errors import CompileError
import sys
import json
import traceback
import os

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def node_to_dict(node, _recurse_level=0):
    _recurse_level += 1
    assert _recurse_level < 5000, "It seems we are recursing..."

    node_name = node.__class__.__name__
    # print((' ' * _recurse_level) + node_name)
    if node_name.endswith("Node"):
        node_name = node_name[:-4]
    data = {"__node__": node_name}
    if _recurse_level == 1:
        data['__version__'] = Cython.__version__

    for attr_name, attr in [(key, value) for key, value in node.__dict__.items()]:
        if attr_name in ("pos", "position"):
            data["line"] = attr[1]
            data["col"] = attr[2]
            continue

        if isinstance(attr, Nodes.Node):
            data[attr_name] = node_to_dict(attr, _recurse_level)

        elif isinstance(attr, (list, tuple)):
            lst = []

            for x in attr:
                if isinstance(x, Nodes.Node):
                    lst.append(node_to_dict(x, _recurse_level))

                elif isinstance(x, (list, tuple)):
                    tup = []

                    for y in x:
                        if isinstance(y, (str, bytes)):
                            tup.append(y)
                        elif isinstance(y, Nodes.Node):
                            tup.append(node_to_dict(y, _recurse_level))

                    lst.append(tup)

            data[attr_name] = lst

        else:
            data[attr_name] = str(attr)

    return data


def source_to_dict(source, name=None):
    from Cython.Compiler.TreeFragment import TreeFragment
    try:
        fragment = TreeFragment(source, name=name)
    except CompileError as e:
        as_dict = node_to_dict(e)
        as_dict['is_error'] = True
        return as_dict

    root = fragment.root
    return node_to_dict(root)


from _pydev_bundle import pydev_localhost
HOST = pydev_localhost.get_localhost()  # Symbolic name meaning the local host
IS_PYTHON_3_ONWARDS = sys.version_info[0] >= 3


def dbg(s):
    sys.stderr.write('%s\n' % (s,))
#        f = open('c:/temp/test.txt', 'a')
#        print_ >> f, s
#        f.close()


SERVER_NAME = 'CythonJson'


class Exit(Exception):
    pass


class CythonJsonServer(object):

    def __init__(self, port):
        self.ended = False
        self._buffer = b''
        self.port = port
        self.socket = None  # socket to send messages.
        self.exit_process_on_kill = True

    def emulated_sendall(self, msg):
        MSGLEN = 1024 * 20

        totalsent = 0
        while totalsent < MSGLEN:
            sent = self.socket.send(msg[totalsent:])
            if sent == 0:
                return
            totalsent = totalsent + sent

    def send(self, msg):
        if not isinstance(msg, bytes):
            msg = msg.encode('utf-8', 'replace')

        if not hasattr(self.socket, 'sendall'):
            # Older versions (jython 2.1)
            self.emulated_sendall(msg)
        else:
            if IS_PYTHON_3_ONWARDS:
                self.socket.sendall(msg)
            else:
                self.socket.sendall(msg)

    def connect_to_server(self):
        from _pydev_imps._pydev_saved_modules import socket

        self.socket = s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            s.connect((HOST, self.port))
        except:
            sys.stderr.write('Error on connect_to_server with parameters: host: %s port: %s\n' % (HOST, self.port))
            raise

    def _read(self, size):
        while True:
            buffer_len = len(self._buffer)
            if buffer_len == size:
                ret = self._buffer
                self._buffer = b''
                return ret

            if buffer_len > size:
                ret = self._buffer[:size]
                self._buffer = self._buffer[size:]
                return ret

            try:
                r = self.socket.recv(max(size - buffer_len, 1024))
            except OSError:
                return b''
            if not r:
                return b''
            self._buffer += r

    def _read_line(self):
        while True:
            i = self._buffer.find(b'\n')
            if i != -1:
                i += 1  # Add the newline to the return
                ret = self._buffer[:i]
                self._buffer = self._buffer[i:]
                return ret
            else:
                try:
                    r = self.socket.recv(1024)
                except OSError:
                    return b''
                if not r:
                    return b''
                self._buffer += r

    def process_command(self, json_contents):
        try:
            as_dict = json.loads(json_contents)
            if as_dict['command'] == 'cython_to_json_ast':
                contents = as_dict['contents']
                as_dict = source_to_dict(contents)
                result = as_dict
            else:
                result = {'command': '<unexpected>', 'received': json_contents}
        except:
            try:
                from StringIO import StringIO
            except:
                from io import StringIO
            s = StringIO()
            traceback.print_exc(file=s)
            result = {'command': '<errored>', 'error': s.getvalue()}

        return json.dumps(result)

    def run(self):
        # Echo server program
        try:
            dbg(SERVER_NAME + ' connecting to java server on %s (%s)' % (HOST, self.port))
            # after being connected, create a socket as a client.
            self.connect_to_server()

            dbg(SERVER_NAME + ' Connected to java server')

            content_len = -1
            while True:
                dbg('Will read line...')
                line = self._read_line()
                dbg('Read: %s' % (line,))
                if not line:
                    raise Exit()

                if line.startswith(b'Content-Length:'):
                    content_len = int(line.strip().split(b':', 1)[1])
                    dbg('Found content len: %s' % (content_len,))
                    continue

                if content_len != -1:
                    # If we previously received a content length, read until a '\r\n'.
                    if line == b'\r\n':
                        dbg('Will read contents (%s)...' % (content_len,))
                        json_contents = self._read(content_len)
                        dbg('Read: %s' % (json_contents,))
                        content_len = -1

                        if len(json_contents) == 0:
                            raise Exit()

                        # We just received a json message, let's process it.
                        dbg('Will process...')
                        output = self.process_command(json_contents)
                        if not isinstance(output, bytes):
                            output = output.encode('utf-8', 'replace')

                        self.send('Content-Length: %s\r\n\r\n' % (len(output),))
                        self.send(output)

                    continue

        except Exit:
            sys.exit(0)
        except:
            traceback.print_exc()
            raise


if __name__ == '__main__':
    args = sys.argv[1:]
    if args == ['-']:
        # Read from stdin/dump to stdout
        if sys.version_info < (3,):
            stdin_get_value = sys.stdin.read
        else:
            stdin_get_value = sys.stdin.buffer.read

        source = stdin_get_value()
        # After reading, convert to unicode (use the stdout encoding)
        source = source.decode(sys.stdout.encoding, 'replace')
        as_dict = source_to_dict(source)
        print(json.dumps(as_dict, indent=4))
        sys.stdout.flush()
    else:
        # start as server
        port = int(sys.argv[1])  # this is from where we want to receive messages.

        t = CythonJsonServer(port)
        dbg(SERVER_NAME + ' will start')
        t.run()

