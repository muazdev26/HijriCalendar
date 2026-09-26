import UIKit
import SwiftUI
import calendar

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @State private var showingWidgetCatalog = false

    var body: some View {
        NavigationStack {
            ComposeView()
                .ignoresSafeArea(.keyboard)
                .ignoresSafeArea(.container, edges: .horizontal)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            showingWidgetCatalog = true
                        } label: {
                            Text("Widgets")
                        }
                    }
                }
        }
        .tint(Color(red: 0.13, green: 0.45, blue: 0.35))
        .sheet(isPresented: $showingWidgetCatalog) {
            WidgetCatalogView()
        }
    }
}
