package com.github.mustachejava.codes;

import com.github.mustachejava.*;
import com.github.mustachejava.util.LatchedWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class PartialCode extends DefaultCode {
  private final DefaultMustacheFactory cf;
  private final ExecutorService les;
  protected final String extension;
  protected Mustache partial;

  protected PartialCode(TemplateContext tc, DefaultMustacheFactory cf, Mustache mustache, String type, String variable) {
    super(tc, cf.getObjectHandler(), mustache, variable, type);
    this.cf = cf;
    // Use the  name of the parent to get the name of the partial
    int index = tc.file().lastIndexOf(".");
    extension = index == -1 ? "" : tc.file().substring(index);
    les = cf.getExecutorService();
  }

  public PartialCode(TemplateContext tc, DefaultMustacheFactory cf, String variable) {
    this(tc, cf, null, ">", variable);
  }

  @Override
  public void identity(Writer writer) {
    try {
      if (name != null) {
        super.tag(writer, type);
      }
      appendText(writer);
    } catch (IOException e) {
      throw new MustacheException(e);
    }
  }

  @Override
  public Code[] getCodes() {
    return partial.getCodes();
  }

  @Override
  public void setCodes(Code[] newcodes) {
    partial.setCodes(newcodes);
  }

  @Override
  public Writer execute(Writer writer, final Object[] scopes) {
    return partialExecute(writer, scopes);
  }

  @Override
  public synchronized void init() {
    partial = cf.compile(partialName());
    if (partial == null) {
      throw new MustacheException("Failed to compile partial: " + name);
    }
  }

  /**
   * Builds the file name to be included by this partial tag. Default implementation ppends the tag contents with
   * the current file's extension.
   *
   * @return The filename to be included by this partial tag
   */
  protected String partialName() {
    return name + extension;
  }

  protected Writer partialExecute(Writer writer, final Object[] scopes) {
    Object object = get(scopes);
    if (object instanceof Callable) {
      // Flush the current writer
      try {
        writer.flush();
      } catch (IOException e) {
        throw new MustacheException("Failed to flush writer", e);
      }
      final Callable callable = (Callable) object;
      if (les == null) {
        try {
          object = callable.call();
        } catch (Exception e) {
          throw new MustacheException(e);
        }
      } else {
        final LatchedWriter latchedWriter = new LatchedWriter(writer);
        final Writer finalWriter = writer;
        les.execute(new Runnable() {
          @Override
          public void run() {
            try {
              execute(finalWriter, callable.call(), scopes);
              latchedWriter.done();
            } catch (Throwable e) {
              latchedWriter.failed(e);
            }
          }
        });
        return latchedWriter;
      }
    }
    return execute(writer, object, scopes);
  }

  protected Writer execute(Writer writer, Object scope, Object[] scopes) {
    Object[] newscopes = addScope(scopes, scope);
    return appendText(partial.execute(writer, newscopes));
  }
}
