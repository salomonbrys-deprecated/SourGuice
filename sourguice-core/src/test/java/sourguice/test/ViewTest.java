package sourguice.test;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.testing.HttpTester;
import org.testng.annotations.Test;

import com.github.sourguice.MvcControlerModule;
import com.github.sourguice.annotation.controller.ViewSystem;
import com.github.sourguice.annotation.request.RequestMapping;
import com.github.sourguice.annotation.request.View;
import com.github.sourguice.view.Model;
import com.github.sourguice.view.def.BasicViewRenderer;
import com.google.inject.Singleton;

@SuppressWarnings({"javadoc", "static-method"})
public class ViewTest extends TestBase {

    // ===================== RENDERERS =====================

	public static class DefaultTestRenderer extends BasicViewRenderer {
		@Inject
		public DefaultTestRenderer(HttpServletResponse res) { super(res); }

		@RenderFor("anno.view")
		public void annodir(PrintWriter writer, Map<String, Object> model) {
			writer.write("d:anno:" + model.get("name").toString());
		}
	}

	public static class AnnoTestRenderer extends BasicViewRenderer {
		@Inject
		public AnnoTestRenderer(HttpServletResponse res) { super(res); }

		@RenderFor("/views/annodir.view")
		public void annodir(PrintWriter writer, Map<String, Object> model) {
			writer.write("a:annodir:" + model.get("name").toString());
		}

		@RenderFor("/annoroot.view")
		public void annoroot(PrintWriter writer, Map<String, Object> model) {
			writer.write("a:annoroot:" + model.get("name").toString());
		}

		@RenderFor("/views/RV.view")
		public void rv(PrintWriter writer, Map<String, Object> model) {
			writer.write("a:return:" + model.get("name").toString());
		}

		@RenderFor("/views/dir/in.view")
		public void dirin(PrintWriter writer, Map<String, Object> model) {
			writer.write("a:dirin:" + model.get("name").toString());
		}
	}

    // ===================== CONTROLLERS =====================

	@Singleton
    @ViewSystem(directory = "/views", renderer = AnnoTestRenderer.class)
    public static class AController {
        @SuppressWarnings("serial")
		@RequestMapping("/annodir")
        @View("annodir.view")
        public void annodir(Model model) {
        	model.put("name", "Choucroute");
        	model.addAllAttributes(new HashMap<String, String>() {{ put("name", "Salomon"); put("truc", "bidule"); }});
        }

        @SuppressWarnings("serial")
        @RequestMapping("/annoroot")
        @View("/annoroot.view")
        public void annoroot(Model model) {
        	model.put("name", "Salomon");
        	model.mergeAttributes(new HashMap<String, String>() {{ put("name", "Choucroute"); put("truc", "bidule"); }});
        }

        @RequestMapping("/returnview")
        public String returnview(Model model) {
        	model.put("name", "Salomon");
        	return "RV.view";
        }

        @RequestMapping("/dirin")
        @View("{}/in.view")
        public String dirin(Model model) {
        	model.put("name", "Salomon");
        	return "dir";
        }
	}

    @Singleton
    public static class DController {
        @RequestMapping("/anno")
        @View("anno.view")
        public void anno(Model model) {
        	model.put("name", "Salomon");
        }

        @RequestMapping("/noview")
        @View("noview.view")
        public void noview() { /**/ }
}

    // ===================== MODULE =====================

    public static class ControllerModule extends MvcControlerModule {
        @Override
        protected void configureControllers() {
            control("/a/*").with(AController.class);
            control("/d/*").with(DController.class);
            renderViewsWith(DefaultTestRenderer.class);
        }
    }

    @Override
    protected MvcControlerModule module() {
        return new ControllerModule();
    }

    // ===================== TESTS =====================

    @Test
    public void getAAnnoDir() throws Exception {
        HttpTester request = makeRequest("GET", "/a/annodir");

        HttpTester response = getResponse(request);

        assert response.getStatus() == 200;
        assert response.getContent().equals("a:annodir:Salomon");
    }

    @Test
    public void getAAnnoRoot() throws Exception {
        HttpTester request = makeRequest("GET", "/a/annoroot");

        HttpTester response = getResponse(request);

        assert response.getStatus() == 200;
        assert response.getContent().equals("a:annoroot:Salomon");
    }

    @Test
    public void getAReturnView() throws Exception {
        HttpTester request = makeRequest("GET", "/a/returnview");

        HttpTester response = getResponse(request);

        assert response.getStatus() == 200;
        assert response.getContent().equals("a:return:Salomon");
    }

    @Test
    public void getADirIn() throws Exception {
        HttpTester request = makeRequest("GET", "/a/dirin");

        HttpTester response = getResponse(request);

        assert response.getStatus() == 200;
        assert response.getContent().equals("a:dirin:Salomon");
    }

    @Test
    public void getDAnno() throws Exception {
        HttpTester request = makeRequest("GET", "/d/anno");

        HttpTester response = getResponse(request);

        assert response.getStatus() == 200;
        assert response.getContent().equals("d:anno:Salomon");
    }

    @Test
    public void getDNoView() throws Exception {
        HttpTester request = makeRequest("GET", "/d/noview");

        HttpTester response = getResponse(request);

        assert response.getStatus() == 500;
        assert response.getReason().equals("sourguice.test.ViewTest.DefaultTestRenderer has no method annotated with @RenderFor(\"noview.view\")");
    }

}

