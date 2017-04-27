package ch.sourcemotion.vertx.dart.sockjs;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author Michel Werren
 */
abstract class AbstractClientServerTest
{
    private static final String WORK_DIR_CONFIG_KEY = "working.dir";

    private Path workDirPath;

    private Properties config;

    private Path stdout;

    private Path errout;


    /**
     * Mandatory to call before a client side test.
     *
     * @throws IOException
     */
    void prepareClientsideTest () throws IOException
    {
        try ( InputStream is = getClass().getResourceAsStream( "/test-config.properties" ) )
        {
            config = new Properties();
            config.load( is );
        }

        workDirPath = Paths.get( config.getProperty( WORK_DIR_CONFIG_KEY ) );

        stdout = Paths.get( config.getProperty( WORK_DIR_CONFIG_KEY ) + "/target/dart-stdout.txt" );
        errout = Paths.get( config.getProperty( WORK_DIR_CONFIG_KEY ) + "/target/dart-err.txt" );

        if ( Files.exists( stdout ) )
        {
            Files.delete( stdout );
        }
        Files.createFile( stdout );
        if ( Files.exists( errout ) )
        {
            Files.delete( errout );
        }
        Files.createFile( errout );
    }


    /**
     * Starts client side test. Use Vert.x unit api to determine test result for the client.
     *
     * Notice, the given {@link Async} will be called exactly one time through this method.
     *
     * @param context
     * @param async
     * @param testFile
     * @throws Exception
     */
    void startTestClient ( TestContext context, Async async, String testFile ) throws Exception
    {
        final CompletableFuture<Integer> out = new CompletableFuture<>();
        new Thread( () ->
        {
            try
            {
                final int processResult = new ProcessBuilder( "pub", "run", "test", "-p", "dartium", testFile ).directory(
                        workDirPath.toFile() ).redirectError( errout.toFile() ).redirectOutput( stdout.toFile() ).start().waitFor();
                out.complete( processResult == 0 && checkLogForTestSuccess() ? 0 : 1 );
            }
            catch ( InterruptedException | IOException e )
            {
                e.printStackTrace();
            }
        } ).start();

        new Thread( () ->
        {
            try
            {
                final Integer exitCode = out.get();
                context.assertEquals( 0, exitCode, "Test has failed on the client side. Exit code:" + exitCode );
            }
            catch ( InterruptedException | ExecutionException e )
            {
                context.fail( e );
            }
            finally
            {
                async.countDown();
            }
        } ).start();
    }


    /**
     * Parses the log of the client side test for test failing message.
     *
     * @return <code>true</code> when the client side test parse has succeeded. Otherwise <code>false</code>
     *
     * @throws IOException
     */
    private boolean checkLogForTestSuccess () throws IOException
    {
        // Unit failures are not a execution failure, so here the log is expected
        final Scanner stdOutScanner = new Scanner( stdout );
        while ( stdOutScanner.hasNextLine() )
        {
            final String line = stdOutScanner.nextLine();
            if ( line.contains( "test" ) && line.contains( "failed" ) )
            {
                return false;
            }
        }
        return true;
    }
}