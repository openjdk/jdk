        }
        return in.read(b, off, len);
    }

    @Override
    public int available() throws IOException {
        if (eof) {
            return 0;
        }
        return in.available();
    }

    @Override
    public void reset() throws IOException {
        throw new IOException("mark/reset not supported");
    }

    @Override
    public void close() throws IOException {
        closed = true;
        t.getServerImpl().requestCompleted(t.getConnection());
    }
}
