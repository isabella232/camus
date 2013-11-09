package com.linkedin.camus.etl.kafka.common;

import com.linkedin.camus.coders.CamusWrapper;
import com.linkedin.camus.etl.IEtlKey;
import com.linkedin.camus.etl.RecordWriterProvider;
import com.linkedin.camus.etl.kafka.mapred.EtlMultiOutputFormat;
import java.io.IOException;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.output.FileOutputCommitter;

import org.apache.log4j.Logger;


/**
 * Provides a RecordWriter that uses FSDataOutputStream to write
 * a String record as bytes to HDFS using gzip compession.
 */
public class StringRecordWriterProvider implements RecordWriterProvider {
    public static final String ETL_OUTPUT_RECORD_DELIMITER = "etl.output.record.delimiter";
    public static final String DEFAULT_RECORD_DELIMITER    = "";

    protected String recordDelimiter = null;

    // TODO: Make this configurable somehow.
    // To do this, we'd have to make RecordWriterProvider have an
    // init(JobContext context) method signature that EtlMultiOutputFormat would always call.
    @Override
    public String getFilenameExtension() {
        return ".gz";
    }

    @Override
    public RecordWriter<IEtlKey, CamusWrapper> getDataRecordWriter(
            final TaskAttemptContext  context,
            String              fileName,
            CamusWrapper        camusWrapper,
            FileOutputCommitter committer) throws IOException, InterruptedException {

        // If recordDelimiter hasn't been initialized, do so now
        if (recordDelimiter == null) {
            recordDelimiter = context.getConfiguration().get(
                ETL_OUTPUT_RECORD_DELIMITER,
                DEFAULT_RECORD_DELIMITER
            );
        }

        // Get the filename for this RecordWriter.
        Path path = new Path(
            committer.getWorkPath(),
            EtlMultiOutputFormat.getUniqueFile(
                context, fileName, getFilenameExtension()
            )
        );

        final FSDataOutputStream writer = path.getFileSystem(context.getConfiguration()).create(path);
        CompressionCodecFactory compressionCodecFactory = new CompressionCodecFactory(context.getConfiguration());
        CompressionCodec compressionCodec = compressionCodecFactory.getCodec(path);
        final CompressionOutputStream  compressionOutputStream = compressionCodec.createOutputStream(writer, compressionCodec.createCompressor());

        return new RecordWriter<IEtlKey, CamusWrapper>() {
            @Override
            public void write(IEtlKey ignore, CamusWrapper data) throws IOException {
                String record = (String)data.getRecord() + recordDelimiter;
                compressionOutputStream.write(record.getBytes());
            }

            @Override
            public void close(TaskAttemptContext context) throws IOException, InterruptedException {
                compressionOutputStream.close();
            }
        };
    }
}
